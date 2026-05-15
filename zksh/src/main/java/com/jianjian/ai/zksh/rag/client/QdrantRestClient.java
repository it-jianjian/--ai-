package com.jianjian.ai.zksh.rag.client;

import com.jianjian.ai.zksh.common.BizException;
import com.jianjian.ai.zksh.rag.config.QdrantProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Qdrant REST 客户端（V1）。
 *
 * <p>职责：
 * - 确保 collection 存在（不存在则按向量维度创建，Cosine 距离）
 * - upsert 单条 point（向量 + payload）</p>
 *
 * <p>说明：V1 走 REST 最小集成，不引入专用 SDK；后续如果要做批量 upsert/检索/过滤，可继续扩展。</p>
 */
@Component
public class QdrantRestClient {

    private final WebClient webClient;
    private final QdrantProperties props;

    public QdrantRestClient(WebClient.Builder builder, QdrantProperties props) {
        this.props = props;
        this.webClient = builder.baseUrl(props.getUrl()).build();
    }

    public Mono<Void> ensureCollectionExists(int vectorSize) {
        String collection = props.getCollection();
        Duration timeout = Duration.ofSeconds(10);

        return webClient.get()
                .uri("/collections/{collection}", collection)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .then()
                .onErrorResume(WebClientResponseException.NotFound.class, e -> createCollection(vectorSize));
    }

    public Mono<Void> deleteCollectionIfExists() {
        String collection = props.getCollection();
        return webClient.delete()
                .uri("/collections/{collection}", collection)
                .retrieve()
                .bodyToMono(Map.class)
                .then()
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
    }

    private Mono<Void> createCollection(int vectorSize) {
        String collection = props.getCollection();
        CreateCollectionRequest req = new CreateCollectionRequest();
        CreateCollectionRequest.VectorsConfig vc = new CreateCollectionRequest.VectorsConfig();
        vc.setSize(vectorSize);
        vc.setDistance("Cosine");
        req.setVectors(vc);

        return webClient.put()
                .uri("/collections/{collection}", collection)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(Map.class)
                .then();
    }

    public Mono<String> upsertPoint(String pointId, List<Double> vector, Map<String, Object> payload) {
        if (vector == null || vector.isEmpty()) {
            return Mono.error(new BizException("qdrant upsert 向量为空"));
        }
        String collection = props.getCollection();
        UpsertPointsRequest req = new UpsertPointsRequest();
        UpsertPointsRequest.Point p = new UpsertPointsRequest.Point();
        p.setId(pointId);
        p.setVector(vector);
        p.setPayload(payload);
        req.setPoints(List.of(p));

        Duration timeout = Duration.ofSeconds(20);
        return webClient.put()
                .uri(uriBuilder -> uriBuilder.path("/collections/{collection}/points").queryParam("wait", "true").build(collection))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .map(ignore -> pointId);
    }

    public Mono<List<SearchResultItem>> search(List<Double> queryVector, Integer limit, Double scoreThreshold) {
        if (queryVector == null || queryVector.isEmpty()) {
            return Mono.just(List.of());
        }
        String collection = props.getCollection();
        SearchRequest req = new SearchRequest();
        req.setVector(queryVector);
        req.setLimit(limit == null ? 3 : limit);
        req.setWithPayload(true);
        if (scoreThreshold != null) {
            req.setScoreThreshold(scoreThreshold);
        }

        return webClient.post()
                .uri("/collections/{collection}/points/search", collection)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(SearchResponse.class)
                .timeout(Duration.ofSeconds(20))
                .map(resp -> {
                    if (resp == null || resp.getResult() == null) {
                        return List.of();
                    }
                    List<SearchResultItem> items = new ArrayList<>();
                    for (SearchResponse.SearchResult result : resp.getResult()) {
                        SearchResultItem item = new SearchResultItem();
                        item.setId(result.getId());
                        item.setScore(result.getScore());
                        item.setPayload(result.getPayload());
                        items.add(item);
                    }
                    return items;
                });
    }

    @Data
    public static class CreateCollectionRequest {
        private VectorsConfig vectors;

        @Data
        public static class VectorsConfig {
            private int size;
            private String distance;
        }
    }

    @Data
    public static class UpsertPointsRequest {
        private List<Point> points;

        @Data
        public static class Point {
            private String id;
            private List<Double> vector;
            private Map<String, Object> payload;
        }
    }

    @Data
    public static class SearchRequest {
        private List<Double> vector;
        private Integer limit;
        @JsonProperty("score_threshold")
        private Double scoreThreshold;
        @JsonProperty("with_payload")
        private Boolean withPayload;
    }

    @Data
    public static class SearchResponse {
        private List<SearchResult> result;

        @Data
        public static class SearchResult {
            private Object id;
            private Double score;
            private Map<String, Object> payload;
        }
    }

    @Data
    public static class SearchResultItem {
        private Object id;
        private Double score;
        private Map<String, Object> payload;
    }
}

