package com.jianjian.ai.zksh.rag.client;

import com.jianjian.ai.zksh.common.BizException;
import com.jianjian.ai.zksh.rag.config.RagRerankProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Compatible / DashScope 兼容的 Rerank 客户端（V1）。
 */
@Component
public class OpenAiCompatibleRerankClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleRerankClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final RagRerankProperties props;

    public OpenAiCompatibleRerankClient(WebClient.Builder builder, RagRerankProperties props) {
        this.props = props;
        String normalizedBaseUrl = normalizeBaseUrl(props.getBaseUrl());
        this.webClient = builder
                .baseUrl(normalizedBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .build();
    }

    public Mono<List<RerankItem>> rerank(String query, List<String> documents, Integer topN) {
        if (query == null || query.isBlank() || documents == null || documents.isEmpty()) {
            return Mono.just(List.of());
        }
        int effectiveTopN = (topN == null || topN <= 0) ? documents.size() : Math.min(topN, documents.size());
        Duration timeout = Duration.ofMillis(props.getTimeoutMs() == null ? 15000 : props.getTimeoutMs());
        Map<String, Object> body = buildRequest(query, documents, effectiveTopN);
        String path = endpointPath();

        return webClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(resp -> {
                    HttpStatusCode status = resp.statusCode();
                    return resp.bodyToMono(String.class).defaultIfEmpty("").flatMap(rawBody -> {
                        if (status.isError()) {
                            log.warn("RERANK HTTP失败：status={} body={}", status.value(), rawBody);
                            return Mono.error(new BizException("rerank HTTP失败: " + status.value()));
                        }
                        try {
                            Map<String, Object> map = MAPPER.readValue(rawBody, Map.class);
                            return Mono.just(map);
                        } catch (Exception e) {
                            log.warn("RERANK解析失败：body={}", rawBody);
                            return Mono.error(new BizException("rerank 响应解析失败"));
                        }
                    });
                })
                .timeout(timeout)
                .doOnSubscribe(s -> log.debug("RERANK HTTP请求：baseUrl={} model={} docs={} topN={} timeoutMs={}",
                        props.getBaseUrl(),
                        props.getModelName(),
                        documents.size(),
                        effectiveTopN,
                        timeout.toMillis()))
                .map(resp -> parseResponse(resp, documents.size()));
    }

    private Map<String, Object> buildRequest(String query, List<String> documents, int topN) {
        String provider = props.getProvider() == null ? "" : props.getProvider().trim().toLowerCase();
        if ("dashscope".equals(provider)) {
            String model = props.getModelName() == null ? "" : props.getModelName().trim();

            // qwen3-rerank：DashScope 文档要求扁平结构（不使用 input/parameters 包装）
            if ("qwen3-rerank".equalsIgnoreCase(model)) {
                Map<String, Object> req = new HashMap<>();
                req.put("model", model);
                req.put("query", query);
                req.put("documents", documents);
                req.put("top_n", topN);
                return req;
            }

            Map<String, Object> input = new HashMap<>();
            input.put("query", query);

            // qwen3-vl-rerank：documents 元素支持 {"text": "..."} / image / video
            if ("qwen3-vl-rerank".equalsIgnoreCase(model)) {
                List<Map<String, Object>> dsDocs = new ArrayList<>();
                for (String doc : documents) {
                    Map<String, Object> d = new HashMap<>();
                    d.put("text", doc);
                    dsDocs.add(d);
                }
                input.put("documents", dsDocs);
            } else {
                // gte-rerank-v2 等：documents 必须是字符串数组
                input.put("documents", documents);
            }

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("top_n", topN);

            Map<String, Object> req = new HashMap<>();
            req.put("model", model);
            req.put("input", input);
            req.put("parameters", parameters);
            return req;
        }

        Map<String, Object> req = new HashMap<>();
        req.put("model", props.getModelName());
        req.put("query", query);
        req.put("documents", documents);
        req.put("top_n", topN);
        return req;
    }

    private String endpointPath() {
        String provider = props.getProvider() == null ? "" : props.getProvider().trim().toLowerCase();
        if ("dashscope".equals(provider)) {
            // DashScope rerank endpoint: POST {base_url}/text-rerank/text-rerank
            return "/text-rerank/text-rerank";
        }
        // OpenAI-compatible rerank endpoint
        return "/rerank";
    }

    @SuppressWarnings("unchecked")
    private List<RerankItem> parseResponse(Map<String, Object> resp, int maxDocuments) {
        if (resp == null) {
            return List.of();
        }

        List<Map<String, Object>> rawResults = null;
        Object output = resp.get("output");
        if (output instanceof Map<?, ?> outputMap) {
            Object results = ((Map<String, Object>) outputMap).get("results");
            if (results instanceof List<?> list) {
                rawResults = (List<Map<String, Object>>) list;
            }
        }
        if (rawResults == null) {
            Object data = resp.get("data");
            if (data instanceof List<?> list) {
                rawResults = (List<Map<String, Object>>) list;
            }
        }
        if (rawResults == null) {
            return List.of();
        }

        List<RerankItem> items = new ArrayList<>();
        for (Map<String, Object> raw : rawResults) {
            Integer index = toInt(raw.get("index"));
            Double score = toDouble(raw.get("relevance_score"));
            if (score == null) {
                score = toDouble(raw.get("score"));
            }
            if (index == null || index < 0 || index >= maxDocuments || score == null) {
                continue;
            }
            items.add(new RerankItem(index, score));
        }
        items.sort(Comparator.comparing(RerankItem::score).reversed());
        return items;
    }

    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            throw new BizException("rag.rerank.base-url 未配置");
        }
        String normalized = baseUrl.trim();
        if (normalized.startsWith("$http://") || normalized.startsWith("$https://")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String provider = props.getProvider() == null ? "" : props.getProvider().trim().toLowerCase();
        if ("dashscope".equals(provider)) {
            // DashScope rerank 不在 compatible-mode/v1 下，正确路径在 /api/v1/services/rerank
            if (normalized.contains("dashscope.aliyuncs.com/compatible-mode")) {
                normalized = "https://dashscope.aliyuncs.com/api/v1/services/rerank";
            }
            // 如果用户误填到具体 endpoint，则回退成 base（由 endpointPath() 拼接）
            if (normalized.endsWith("/text-rerank/text-rerank")) {
                normalized = normalized.substring(0, normalized.length() - "/text-rerank/text-rerank".length());
            }
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new BizException("rag.rerank.base-url 非法: " + normalized);
        }
        return normalized;
    }

    public record RerankItem(Integer index, Double score) {
    }
}

