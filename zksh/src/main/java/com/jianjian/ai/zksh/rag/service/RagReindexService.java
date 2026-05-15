package com.jianjian.ai.zksh.rag.service;

import com.jianjian.ai.zksh.common.BizException;
import com.jianjian.ai.zksh.domain.entity.KnowledgeChunkEntity;
import com.jianjian.ai.zksh.domain.entity.KnowledgeDocumentEntity;
import com.jianjian.ai.zksh.mapper.KnowledgeChunkMapper;
import com.jianjian.ai.zksh.mapper.KnowledgeDocumentMapper;
import com.jianjian.ai.zksh.rag.client.OpenAiCompatibleEmbeddingClient;
import com.jianjian.ai.zksh.rag.client.QdrantRestClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 一键重建 RAG 索引：清空 Qdrant 集合并按用户文档重新 embedding + upsert。
 */
@Service
public class RagReindexService {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final OpenAiCompatibleEmbeddingClient embeddingClient;
    private final QdrantRestClient qdrantRestClient;

    public RagReindexService(KnowledgeDocumentMapper knowledgeDocumentMapper,
                             KnowledgeChunkMapper knowledgeChunkMapper,
                             OpenAiCompatibleEmbeddingClient embeddingClient,
                             QdrantRestClient qdrantRestClient) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.embeddingClient = embeddingClient;
        this.qdrantRestClient = qdrantRestClient;
    }

    public ReindexResult reindexMyKnowledge(Long userId) {
        if (userId == null) {
            throw new BizException("用户未登录，无法重建索引");
        }
        List<KnowledgeDocumentEntity> docs = knowledgeDocumentMapper.selectByCreatedBy(userId);
        int docCount = docs == null ? 0 : docs.size();
        if (docCount == 0) {
            qdrantRestClient.deleteCollectionIfExists().block();
            return new ReindexResult(0, 0, "当前用户无知识文档，已清空集合");
        }

        qdrantRestClient.deleteCollectionIfExists().block();

        int successChunks = 0;
        for (KnowledgeDocumentEntity doc : docs) {
            List<KnowledgeChunkEntity> chunks = knowledgeChunkMapper.selectByDocId(doc.getId());
            for (KnowledgeChunkEntity chunk : chunks) {
                String content = chunk.getContent();
                if (content == null || content.isBlank()) {
                    continue;
                }
                List<Double> vector = embeddingClient.embedOne(content).block();
                if (vector == null || vector.isEmpty()) {
                    continue;
                }
                qdrantRestClient.ensureCollectionExists(vector.size()).block();
                String pointId = UUID.randomUUID().toString().replace("-", "");
                Map<String, Object> payload = new HashMap<>();
                payload.put("docId", doc.getId());
                payload.put("chunkId", chunk.getId());
                payload.put("title", doc.getTitle());
                payload.put("source", doc.getSource());
                payload.put("tags", doc.getTags());
                payload.put("chunkIndex", chunk.getChunkIndex());

                qdrantRestClient.upsertPoint(pointId, vector, payload).block();
                knowledgeChunkMapper.updateQdrantPointId(chunk.getId(), pointId);
                successChunks++;
            }
        }
        return new ReindexResult(docCount, successChunks, "重建完成");
    }

    public record ReindexResult(Integer docCount, Integer chunkCount, String message) {
    }
}

