package com.jianjian.ai.zksh.rag.service;

import com.jianjian.ai.zksh.domain.entity.KnowledgeChunkEntity;
import com.jianjian.ai.zksh.domain.entity.RagRetrievalLogEntity;
import com.jianjian.ai.zksh.domain.vo.AiReferenceVO;
import com.jianjian.ai.zksh.mapper.KnowledgeChunkMapper;
import com.jianjian.ai.zksh.mapper.RagRetrievalLogMapper;
import com.jianjian.ai.zksh.rag.config.RagHybridProperties;
import com.jianjian.ai.zksh.rag.client.OpenAiCompatibleEmbeddingClient;
import com.jianjian.ai.zksh.rag.client.QdrantRestClient;
import com.jianjian.ai.zksh.rag.client.OpenAiCompatibleRerankClient;
import com.jianjian.ai.zksh.rag.service.QueryRewriteService.RewriteResult;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**

 /**
 * 基于 Qdrant + MySQL 的混合检索 ContentRetriever 实现
 *
 * <p>核心职责：作为 LangChain4j AiService 的标准内容检索器，实现完整的 RAG（检索增强生成）流程</p>
 *
 * <p>检索架构（Hybrid RAG Pipeline）：</p>
 * <pre>
 * 1. 查询改写（Query Rewrite）
 *    └─ 使用 LLM 将用户问题改写为更适合检索的形式
 *
 * 2. 向量化（Embedding）
 *    ├─ 原始查询 → 向量 A
 *    └─ 改写查询 → 向量 B（如果改写成功）
 *
 * 3. 双路召回（Dual-path Recall）
 *    ├─ 向量检索（Qdrant）
 *    │  ├─ 使用向量 A 检索 → 结果集 1
 *    │  └─ 使用向量 B 检索 → 结果集 2（如果存在）
 *    │  └─ 合并去重 → 向量候选集
 *    └─ 关键词检索（MySQL Fulltext）
 *       └─ 使用原始/改写查询全文搜索 → 关键词候选集
 *
 * 4. 结果融合（Fusion）
 *    └─ 使用 RRF（Reciprocal Rank Fusion）算法融合两路结果
 *
 * 5. 重排序（Rerank）
 *    └─ 使用 Cross-Encoder 模型对融合后的候选集精排
 *
 * 6. 截断与返回
 *    └─ 取 Top-K 结果，构建 Content 列表返回给 LLM
 *
 * 7. 日志记录
 *    └─ 记录检索过程、耗时、命中率等关键指标
 * </pre>
 *
 * <p>容错策略：</p>
 * <ul>
 *   <li>查询改写失败 → 使用原始查询继续</li>
 *   <li>Embedding 失败 → 返回空结果，降级为无检索</li>
 *   <li>Qdrant 不可用 → 返回空结果，保证系统可用性</li>
 *   <li>Rerank 失败 → 使用向量相似度排序作为备选</li>
 * </ul>
 */
@Component("contentRetriever")
public class QdrantContentRetriever implements ContentRetriever {
    private static final Logger log = LoggerFactory.getLogger(QdrantContentRetriever.class);

    private final OpenAiCompatibleEmbeddingClient embeddingClient;
    private final QdrantRestClient qdrantRestClient;
    private final RerankService rerankService;
    private final QueryRewriteService queryRewriteService;
    private final RagHybridProperties ragHybridProperties;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final RagRetrievalLogMapper ragRetrievalLogMapper;
    private final RagReferenceStore ragReferenceStore;

    public QdrantContentRetriever(OpenAiCompatibleEmbeddingClient embeddingClient,
                                  QdrantRestClient qdrantRestClient,
                                  RerankService rerankService,
                                  QueryRewriteService queryRewriteService,
                                  RagHybridProperties ragHybridProperties,
                                  KnowledgeChunkMapper knowledgeChunkMapper,
                                  RagRetrievalLogMapper ragRetrievalLogMapper,
                                  RagReferenceStore ragReferenceStore) {
        this.embeddingClient = embeddingClient;
        this.qdrantRestClient = qdrantRestClient;
        this.rerankService = rerankService;
        this.queryRewriteService = queryRewriteService;
        this.ragHybridProperties = ragHybridProperties;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.ragRetrievalLogMapper = ragRetrievalLogMapper;
        this.ragReferenceStore = ragReferenceStore;
    }
    /**
     * 核心检索方法：实现 LangChain4j ContentRetriever 接口
     *
     * <p>这是 RAG 流程的入口点，由 AiService 自动调用</p>
     *
     * <p>完整执行流程（14 个步骤）：</p>
     * <ol>
     *   <li>参数校验：检查查询是否为空，空查询直接返回空结果</li>
     *   <li>查询改写：尝试使用 LLM 优化查询文本（可选，失败不影响主流程）</li>
     *   <li>向量化：将原始查询和改写查询分别转换为向量</li>
     *   <li>确保集合存在：检查 Qdrant 中是否存在对应的向量集合</li>
     *   <li>并行向量检索：同时使用原始向量和改写向量检索（异步提升性能）</li>
     *   <li>关键词检索：使用 MySQL 全文索引进行关键词匹配</li>
     *   <li>结果合并：合并两路向量检索结果，去重并保留最高分</li>
     *   <li>映射到实体：从 Qdrant 的 pointId/chunkId 映射到 MySQL 中的 KnowledgeChunkEntity</li>
     *   <li>构建候选集：组装向量分数、标题等元数据</li>
     *   <li>混合融合：使用 RRF 算法融合向量检索和关键词检索的结果</li>
     *   <li>截断控制：限制候选集大小，避免重排序开销过大</li>
     *   <li>重排序：使用 Cross-Encoder 模型对候选集精排</li>
     *   <li>构建结果：提取 Top-K 结果，构建 Content 列表和引用列表</li>
     *   <li>日志记录：记录检索过程的详细信息到数据库</li>
     * </ol>
     *
     * <p>降级策略：</p>
     * <ul>
     *   <li>Embedding 失败 → 立即返回空结果，避免阻塞</li>
     *   <li>Qdrant 不可用 → 返回空结果，保证系统稳定性</li>
     *   <li>无召回结果 → 返回空列表，让 LLM 基于自身知识回答</li>
     *   <li>Rerank 失败 → 使用向量相似度排序作为备选方案</li>
     * </ul>
     *
     * @param query LangChain4j 查询对象，包含查询文本和元数据（如 userId、sessionId）
     * @return 检索到的相关内容列表，每个 Content 包含一个 TextSegment
     */
    @Override
    public List<Content> retrieve(Query query) {
        long begin = System.currentTimeMillis();
        String queryText = query == null || query.text() == null ? "" : query.text();
        String memoryId = extractMemoryId(query);
        if (queryText.isBlank()) {
            ragReferenceStore.save(memoryId, List.of());
            return List.of();
        }

        RewriteResult rewrite = null;
        try {
            rewrite = queryRewriteService.rewriteOrNull(queryText);
        } catch (Exception e) {
            // 改写失败不影响主流程
        }
        String rewrittenQuery = rewrite == null ? null : rewrite.rewrittenQuery();
        if (rewrittenQuery != null && !rewrittenQuery.isBlank()) {
            log.info("REWRITE命中：original='{}' rewritten='{}'", queryText, rewrittenQuery);
        }

        List<Double> queryVector;
        try {
            queryVector = embeddingClient.embedOne(queryText).block();
        } catch (Exception e) {
            log.warn("RAG embedding 失败，降级为无检索。query={}", queryText, e);
            ragReferenceStore.save(memoryId, List.of());
            writeLog(query, queryText, 0, 0, begin, buildSnapshotJson(List.of(), rewrite));
            return List.of();
        }
        List<Double> rewrittenVector = null;
        if (rewrittenQuery != null && !rewrittenQuery.isBlank()) {
            try {
                rewrittenVector = embeddingClient.embedOne(rewrittenQuery).block();
            } catch (Exception e) {
                rewrittenVector = null;
            }
        }

        List<QdrantRestClient.SearchResultItem> qdrantResults;
        List<KnowledgeChunkEntity> keywordChunks = List.of();
        try {
            qdrantRestClient.ensureCollectionExists(queryVector.size()).block();
            final List<Double> finalQueryVector = queryVector;
            final List<Double> finalRewrittenVector = rewrittenVector;
            CompletableFuture<List<QdrantRestClient.SearchResultItem>> originalFuture =
                    CompletableFuture.supplyAsync(() -> qdrantRestClient
                            .search(finalQueryVector, vectorRecallTopN(), vectorScoreThreshold())
                            .block());
            CompletableFuture<List<QdrantRestClient.SearchResultItem>> rewrittenFuture = null;
            if (finalRewrittenVector != null && !finalRewrittenVector.isEmpty()) {
                rewrittenFuture = CompletableFuture.supplyAsync(() -> qdrantRestClient
                        .search(finalRewrittenVector, vectorRecallTopN(), vectorScoreThreshold())
                        .block());
            }
            List<QdrantRestClient.SearchResultItem> originalResults = originalFuture.orTimeout(2, TimeUnit.SECONDS).join();
            List<QdrantRestClient.SearchResultItem> rewrittenResults = rewrittenFuture == null ? List.of() : rewrittenFuture.orTimeout(2, TimeUnit.SECONDS).join();
            qdrantResults = mergeResults(originalResults, rewrittenResults);
            keywordChunks = keywordRecall(queryText, rewrittenQuery);
            log.info("RAG-HYBRID召回：query='{}' vectorOriginal={} vectorRewrite={} vectorMerged={} keyword={}",
                    queryText,
                    originalResults == null ? 0 : originalResults.size(),
                    rewrittenResults == null ? 0 : rewrittenResults.size(),
                    qdrantResults == null ? 0 : qdrantResults.size(),
                    keywordChunks == null ? 0 : keywordChunks.size());
        } catch (Exception e) {
            log.warn("Qdrant 不可用，降级为无检索。url=localhost:6333", e);
            ragReferenceStore.save(memoryId, List.of());
            writeLog(query, queryText, 0, 0, begin, buildSnapshotJson(List.of(), rewrite));
            return List.of();
        }
        if (qdrantResults == null || qdrantResults.isEmpty()) {
            log.info("RAG检索完成：query='{}' recall=0 topk=0", queryText);
            ragReferenceStore.save(memoryId, List.of());
            writeLog(query, queryText, 0, 0, begin, buildSnapshotJson(List.of(), rewrite));
            return List.of();
        }

        List<Long> chunkIds = qdrantResults.stream()
                .map(item -> item.getPayload() == null ? null : item.getPayload().get("chunkId"))
                .filter(v -> v != null)
                .map(this::toLong)
                .filter(v -> v != null)
                .distinct()
                .toList();
        List<String> pointIds = qdrantResults.stream()
                .map(QdrantRestClient.SearchResultItem::getId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(v -> !v.isBlank())
                .distinct()
                .toList();
        log.info("RAG映射阶段：query='{}' recall={} payloadChunkIds={}", queryText, qdrantResults.size(), chunkIds.size());
        Map<Long, KnowledgeChunkEntity> chunkMap = new HashMap<>();
        if (!chunkIds.isEmpty()) {
            chunkMap.putAll(knowledgeChunkMapper.selectByIds(chunkIds).stream()
                    .collect(Collectors.toMap(KnowledgeChunkEntity::getId, v -> v)));
        }
        Map<String, KnowledgeChunkEntity> chunkByPointId = new HashMap<>();
        if (!pointIds.isEmpty()) {
            List<KnowledgeChunkEntity> byPointId = knowledgeChunkMapper.selectByQdrantPointIds(pointIds);
            for (KnowledgeChunkEntity chunk : byPointId) {
                if (chunk.getQdrantPointId() != null && !chunk.getQdrantPointId().isBlank()) {
                    chunkByPointId.put(chunk.getQdrantPointId(), chunk);
                }
                if (chunk.getId() != null) {
                    chunkMap.putIfAbsent(chunk.getId(), chunk);
                }
            }
        }
        log.info("RAG映射阶段：query='{}' mysqlChunks={} fallbackByPointId={}", queryText, chunkMap.size(), chunkByPointId.size());

        List<CandidateChunk> candidates = new ArrayList<>();
        for (QdrantRestClient.SearchResultItem item : qdrantResults) {
            Long chunkId = item.getPayload() == null ? null : toLong(item.getPayload().get("chunkId"));
            KnowledgeChunkEntity chunk = chunkId == null ? null : chunkMap.get(chunkId);
            if (chunk == null) {
                Object pointIdObj = item.getId();
                String pointId = pointIdObj == null ? null : String.valueOf(pointIdObj);
                if (pointId != null && !pointId.isBlank()) {
                    chunk = chunkByPointId.get(pointId);
                }
            }
            if (chunk == null) {
                continue;
            }
            String title = item.getPayload() == null ? null : String.valueOf(item.getPayload().getOrDefault("title", ""));
            candidates.add(new CandidateChunk(chunk, title, item.getScore() == null ? 0d : item.getScore()));
        }
        if (candidates.isEmpty()) {
            log.info("RAG检索完成：query='{}' recall={} topk=0", queryText, qdrantResults.size());
            ragReferenceStore.save(memoryId, List.of());
            writeLog(query, queryText, qdrantResults.size(), 0, begin, buildSnapshotJson(List.of(), rewrite));
            return List.of();
        }
        candidates = fuseCandidates(candidates, keywordChunks);
        log.info("RAG-HYBRID融合：query='{}' vectorCandidates={} keywordCandidates={} fusedCandidates={}",
                queryText,
                qdrantResults.size(),
                keywordChunks == null ? 0 : keywordChunks.size(),
                candidates.size());
        if (mergeLimit() > 0 && candidates.size() > mergeLimit()) {
            candidates = new ArrayList<>(candidates.subList(0, mergeLimit()));
            log.info("RAG-HYBRID截断：query='{}' mergeLimit={} afterCut={}", queryText, mergeLimit(), candidates.size());
        }

        List<String> rerankDocuments = candidates.stream().map(v -> v.chunk.getContent()).toList();
        String rerankQuery = (rewrittenQuery != null && !rewrittenQuery.isBlank()) ? rewrittenQuery : queryText;
        List<OpenAiCompatibleRerankClient.RerankItem> rerankItems = rerankService.rerank(rerankQuery, rerankDocuments);

        List<CandidateChunk> orderedCandidates;
        if (rerankItems != null && !rerankItems.isEmpty()) {
            orderedCandidates = new ArrayList<>();
            for (OpenAiCompatibleRerankClient.RerankItem rerankItem : rerankItems) {
                Integer idx = rerankItem.index();
                if (idx == null || idx < 0 || idx >= candidates.size()) {
                    continue;
                }
                CandidateChunk candidate = candidates.get(idx);
                candidate.rerankScore = rerankItem.score();
                orderedCandidates.add(candidate);
            }
            if (orderedCandidates.isEmpty()) {
                orderedCandidates = candidates;
            }
        } else {
            orderedCandidates = candidates;
        }

        List<Content> result = orderedCandidates.stream()
                .limit(finalTopK())
                .map(v -> Content.from(TextSegment.from(v.chunk.getContent())))
                .collect(Collectors.toList());
        List<AiReferenceVO> references = orderedCandidates.stream()
                .limit(finalTopK())
                .map(v -> new AiReferenceVO(
                        String.valueOf(v.chunk.getDocId()),
                        v.title == null ? "" : v.title,
                        buildSnippet(v.chunk.getContent())
                ))
                .collect(Collectors.toList());
        ragReferenceStore.save(memoryId, references);

        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (CandidateChunk candidate : orderedCandidates) {
            Map<String, Object> row = new HashMap<>();
            row.put("chunkId", candidate.chunk.getId());
            row.put("docId", candidate.chunk.getDocId());
            row.put("vectorScore", candidate.vectorScore);
            row.put("rerankScore", candidate.rerankScore);
            snapshot.add(row);
        }
        snapshot.sort(Comparator.comparing(m -> -1 * scoreForSort(m)));
        log.info("RAG检索完成：query='{}' recall={} topk={}", queryText, qdrantResults.size(), result.size());
        writeLog(query, queryText, qdrantResults.size(), result.size(), begin, buildSnapshotJson(snapshot, rewrite));
        return result;
    }

    private List<QdrantRestClient.SearchResultItem> mergeResults(List<QdrantRestClient.SearchResultItem> original,
                                                                 List<QdrantRestClient.SearchResultItem> rewritten) {
        List<QdrantRestClient.SearchResultItem> a = original == null ? List.of() : original;
        List<QdrantRestClient.SearchResultItem> b = rewritten == null ? List.of() : rewritten;
        if (b.isEmpty()) {
            return a;
        }
        Map<String, QdrantRestClient.SearchResultItem> best = new HashMap<>();
        for (QdrantRestClient.SearchResultItem item : a) {
            String key = resultKey(item);
            if (key == null) continue;
            best.put(key, item);
        }
        for (QdrantRestClient.SearchResultItem item : b) {
            String key = resultKey(item);
            if (key == null) continue;
            QdrantRestClient.SearchResultItem prev = best.get(key);
            if (prev == null || (item.getScore() != null && prev.getScore() != null && item.getScore() > prev.getScore())) {
                best.put(key, item);
            }
        }
        List<QdrantRestClient.SearchResultItem> merged = new ArrayList<>(best.values());
        merged.sort(Comparator.comparing((QdrantRestClient.SearchResultItem it) -> it.getScore() == null ? 0d : it.getScore()).reversed());
        return merged;
    }

    private String resultKey(QdrantRestClient.SearchResultItem item) {
        if (item == null) return null;
        Object chunkId = item.getPayload() == null ? null : item.getPayload().get("chunkId");
        if (chunkId != null) {
            Long id = toLong(chunkId);
            if (id != null) {
                return "chunk:" + id;
            }
        }
        Object pid = item.getId();
        if (pid != null) {
            String s = String.valueOf(pid).trim();
            if (!s.isBlank()) return "pid:" + s;
        }
        return null;
    }

    private String buildSnapshotJson(List<Map<String, Object>> rows, RewriteResult rewrite) {
        String listJson = toSimpleJson(rows == null ? List.of() : rows);
        if (rewrite == null) {
            return listJson;
        }
        String rewritten = rewrite.rewrittenQuery() == null ? "" : rewrite.rewrittenQuery();
        String raw = rewrite.rawJson() == null ? "" : rewrite.rawJson();
        return "{\"rewrite\":{\"rewrittenQuery\":\"" + escape(rewritten) + "\",\"raw\":\"" + escape(raw) + "\"},\"candidates\":" + listJson + "}";
    }

    private static class CandidateChunk {
        private final KnowledgeChunkEntity chunk;
        private final String title;
        private final Double vectorScore;
        private Double rerankScore;
        private Double fusedScore;

        private CandidateChunk(KnowledgeChunkEntity chunk, String title, Double vectorScore) {
            this.chunk = chunk;
            this.title = title;
            this.vectorScore = vectorScore;
            this.rerankScore = null;
            this.fusedScore = vectorScore;
        }
    }

    private void writeLog(Query query, String queryText, int recallCount, int topkCount, long begin, String snapshotJson) {
        RagRetrievalLogEntity entity = new RagRetrievalLogEntity();
        entity.setQueryText(queryText);
        entity.setRecallCount(recallCount);
        entity.setTopkCount(topkCount);
        entity.setLatencyMs((int) (System.currentTimeMillis() - begin));
        entity.setResultSnapshotJson(snapshotJson);

        String memoryId = null;
        if (query != null && query.metadata() != null && query.metadata().chatMemoryId() != null) {
            memoryId = String.valueOf(query.metadata().chatMemoryId());
        }
        if (memoryId != null && memoryId.contains(":")) {
            String[] parts = memoryId.split(":", 2);
            Long userId = toLong(parts[0]);
            Long sessionId = toLong(parts[1]);
            entity.setUserId(userId == null ? 0L : userId);
            entity.setSessionId(sessionId);
        } else {
            entity.setUserId(0L);
            entity.setSessionId(null);
        }
        ragRetrievalLogMapper.insert(entity);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String toSimpleJson(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder("[");
        boolean firstRow = true;
        for (Map<String, Object> row : rows) {
            if (!firstRow) {
                sb.append(",");
            }
            firstRow = false;
            sb.append("{");
            boolean firstCol = true;
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (!firstCol) {
                    sb.append(",");
                }
                firstCol = false;
                sb.append("\"").append(escape(String.valueOf(e.getKey()))).append("\":");
                Object v = e.getValue();
                if (v == null) {
                    sb.append("null");
                } else if (v instanceof Number || v instanceof Boolean) {
                    sb.append(v);
                } else {
                    sb.append("\"").append(escape(String.valueOf(v))).append("\"");
                }
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String buildSnippet(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        int max = 120;
        if (normalized.length() <= max) {
            return normalized;
        }
        return normalized.substring(0, max) + "...";
    }

    private String extractMemoryId(Query query) {
        if (query == null || query.metadata() == null || query.metadata().chatMemoryId() == null) {
            return null;
        }
        return String.valueOf(query.metadata().chatMemoryId());
    }

    private double scoreForSort(Map<String, Object> row) {
        Object rerank = row.get("rerankScore");
        if (rerank instanceof Number n) {
            return n.doubleValue();
        }
        Object vector = row.get("vectorScore");
        if (vector instanceof Number n) {
            return n.doubleValue();
        }
        return 0d;
    }

    private List<KnowledgeChunkEntity> keywordRecall(String queryText, String rewrittenQuery) {
        if (!hybridEnabled()) {
            return List.of();
        }
        int topN = keywordRecallTopN();
        if (topN <= 0) {
            return List.of();
        }
        List<KnowledgeChunkEntity> merged = new ArrayList<>();
        Set<Long> seen = new java.util.HashSet<>();
        for (String keyword : List.of(queryText, rewrittenQuery == null ? "" : rewrittenQuery)) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            List<KnowledgeChunkEntity> rows = knowledgeChunkMapper.searchByKeyword(keyword.trim(), topN);
            for (KnowledgeChunkEntity row : rows) {
                if (row.getId() != null && seen.add(row.getId())) {
                    merged.add(row);
                }
            }
        }
        return merged;
    }

    private List<CandidateChunk> fuseCandidates(List<CandidateChunk> vectorCandidates, List<KnowledgeChunkEntity> keywordChunks) {
        Map<Long, CandidateChunk> byChunkId = new HashMap<>();
        for (int i = 0; i < vectorCandidates.size(); i++) {
            CandidateChunk item = vectorCandidates.get(i);
            if (item.chunk.getId() == null) {
                continue;
            }
            double rankScore = rrfScore(i + 1);
            item.fusedScore = rankScore;
            byChunkId.put(item.chunk.getId(), item);
        }

        for (int i = 0; i < keywordChunks.size(); i++) {
            KnowledgeChunkEntity chunk = keywordChunks.get(i);
            if (chunk.getId() == null) {
                continue;
            }
            CandidateChunk existing = byChunkId.get(chunk.getId());
            double rankScore = rrfScore(i + 1);
            if (existing != null) {
                existing.fusedScore = existing.fusedScore + rankScore;
                continue;
            }
            CandidateChunk fromKeyword = new CandidateChunk(chunk, "", 0d);
            fromKeyword.fusedScore = rankScore;
            byChunkId.put(chunk.getId(), fromKeyword);
        }

        List<CandidateChunk> merged = new ArrayList<>(byChunkId.values());
        merged.sort(Comparator.comparing((CandidateChunk x) -> x.fusedScore).reversed());
        return merged;
    }

    private double rrfScore(int rank) {
        int k = rrfK();
        return 1d / (k + Math.max(rank, 1));
    }

    private boolean hybridEnabled() {
        return ragHybridProperties.getEnabled() == null || ragHybridProperties.getEnabled();
    }

    private int vectorRecallTopN() {
        return valueOrDefault(ragHybridProperties.getVectorTopN(), 30);
    }

    private int keywordRecallTopN() {
        return valueOrDefault(ragHybridProperties.getKeywordTopN(), 30);
    }

    private int mergeLimit() {
        return valueOrDefault(ragHybridProperties.getMergeLimit(), 50);
    }

    private int finalTopK() {
        return valueOrDefault(ragHybridProperties.getFinalTopK(), 6);
    }

    private int rrfK() {
        return valueOrDefault(ragHybridProperties.getRrfK(), 60);
    }

    private double vectorScoreThreshold() {
        Double score = ragHybridProperties.getVectorScoreThreshold();
        return score == null ? 0.3d : score;
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return value;
    }
}

