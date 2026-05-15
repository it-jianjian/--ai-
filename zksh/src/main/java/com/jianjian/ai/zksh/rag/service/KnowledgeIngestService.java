package com.jianjian.ai.zksh.rag.service;

import com.jianjian.ai.zksh.common.BizException;
import com.jianjian.ai.zksh.domain.entity.KnowledgeChunkEntity;
import com.jianjian.ai.zksh.domain.entity.KnowledgeDocumentEntity;
import com.jianjian.ai.zksh.mapper.KnowledgeChunkMapper;
import com.jianjian.ai.zksh.mapper.KnowledgeDocumentMapper;
import com.jianjian.ai.zksh.rag.client.OpenAiCompatibleEmbeddingClient;
import com.jianjian.ai.zksh.rag.client.QdrantRestClient;
import com.jianjian.ai.zksh.rag.config.RagEmbeddingProperties;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识导入编排服务（V1）。
 *
 * <p>按 LangChain4j 官方 RAG 流程执行：Document -> TextSegment -> Embedding -> Vector Store。</p>
 */
@Service
public class KnowledgeIngestService {

    private final TextExtractService textExtractService;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final OpenAiCompatibleEmbeddingClient embeddingClient;
    private final QdrantRestClient qdrantClient;
    private final OpenAiTokenCountEstimator tokenCountEstimator;

    public KnowledgeIngestService(TextExtractService textExtractService,
                                  KnowledgeDocumentMapper knowledgeDocumentMapper,
                                  KnowledgeChunkMapper knowledgeChunkMapper,
                                  OpenAiCompatibleEmbeddingClient embeddingClient,
                                  QdrantRestClient qdrantClient,
                                  RagEmbeddingProperties ragEmbeddingProperties) {
        this.textExtractService = textExtractService;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.embeddingClient = embeddingClient;
        this.qdrantClient = qdrantClient;

        // OpenAiTokenCountEstimator 依赖 jtokkit 的内置模型映射。
        // 某些 embedding modelName（如 "text-embedding-v3"）可能在当前版本中未知，导致 Spring 启动失败。
        // 这里做一个回退，确保服务可以正常启动（token 估算用于切分策略，不会影响 embedding 本身的请求）。
        String modelName = ragEmbeddingProperties == null ? null : ragEmbeddingProperties.getModelName();
        this.tokenCountEstimator = createTokenCountEstimator(modelName);
    }

    private OpenAiTokenCountEstimator createTokenCountEstimator(String modelName) {
        // 常见可用的 embedding 模型名（至少应被当前 jtokkit 支持）
        String fallback = "text-embedding-3-small";
        if (modelName == null || modelName.isBlank()) {
            return new OpenAiTokenCountEstimator(fallback);
        }
        try {
            return new OpenAiTokenCountEstimator(modelName);
        } catch (IllegalArgumentException e) {
            return new OpenAiTokenCountEstimator(fallback);
        }
    }

    @Transactional
    public KnowledgeDocumentEntity ingest(Long userId, MultipartFile file, String title, String tags) {
        if (file == null || file.isEmpty()) {
            throw new BizException("文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        String docTitle = (title == null || title.isBlank())
                ? (originalName == null ? "未命名文档" : originalName)
                : title;

        KnowledgeDocumentEntity doc = new KnowledgeDocumentEntity();
        doc.setTitle(docTitle);
        doc.setSource(originalName);
        doc.setTags(tags);
        doc.setStatus("INDEXING");
        doc.setChunkCount(0);
        doc.setCreatedBy(userId);
        knowledgeDocumentMapper.insert(doc);
        String text = textExtractService.extractText(file);
        List<TextSegment> segments = splitByOfficialStyle(text, docTitle, originalName, tags);
        if (segments.isEmpty()) {
            knowledgeDocumentMapper.updateStatus(doc.getId(), "FAILED", 0);
            throw new BizException("文档无可用文本内容");
        }
        int idx = 0;
        for (TextSegment segment : segments) {
            String content = segment.text();
            KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
            chunk.setDocId(doc.getId());
            chunk.setChunkIndex(idx++);
            chunk.setContent(content);
            chunk.setTokenCount(tokenCountEstimator.estimateTokenCountInText(content));
            chunk.setQdrantPointId(null);
            chunk.setMetadataJson(toMetadataJson(segment.metadata()));
            knowledgeChunkMapper.insert(chunk);

            List<Double> vector = embeddingClient.embedOne(content).block();
            if (vector == null || vector.isEmpty()) {
                throw new BizException("embedding 失败：返回空向量");
            }

            qdrantClient.ensureCollectionExists(vector.size()).block();

            String pointId = UUID.randomUUID().toString().replace("-", "");
            Map<String, Object> payload = new HashMap<>();
            payload.put("docId", doc.getId());
            payload.put("chunkId", chunk.getId());
            payload.put("title", doc.getTitle());
            payload.put("source", doc.getSource());
            payload.put("tags", doc.getTags());
            payload.put("chunkIndex", chunk.getChunkIndex());
            payload.put("metadata", segment.metadata().toMap());

            qdrantClient.upsertPoint(pointId, vector, payload).block();
            knowledgeChunkMapper.updateQdrantPointId(chunk.getId(), pointId);
        }

        knowledgeDocumentMapper.updateStatus(doc.getId(), "READY", segments.size());
        doc.setStatus("READY");
        doc.setChunkCount(segments.size());
        return doc;
    }

    private List<TextSegment> splitByOfficialStyle(String text, String title, String source, String tags) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        // 创建 Metadata
        Metadata metadata = new Metadata();
        metadata.put("title", title == null ? "" : title);
        metadata.put("source", source == null ? "" : source);
        metadata.put("tags", tags == null ? "" : tags);
        Document document = Document.from(text, metadata);

        // 按 token 切分并重叠：上限 500、重叠 100
        return DocumentSplitters.recursive(500, 100, tokenCountEstimator).split(document);
    }

    private String toMetadataJson(Metadata metadata) {
        if (metadata == null) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : metadata.toMap().entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(escapeJson(e.getKey())).append("\":");
            if (e.getValue() == null) {
                sb.append("null");
            } else {
                sb.append("\"").append(escapeJson(String.valueOf(e.getValue()))).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

