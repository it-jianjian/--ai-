package com.jianjian.ai.zksh.rag.client;

import com.jianjian.ai.zksh.common.BizException;
import com.jianjian.ai.zksh.rag.config.RagEmbeddingProperties;
import lombok.Data;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * OpenAI 兼容 Embedding 客户端（V1）。
 *
 * <p>对接形如 {@code POST /embeddings} 的 OpenAI Compatible API，返回向量。</p>
 * <p>注意：这里只做最小字段解析（data[0].embedding），不处理多输入批量与用量字段。</p>
 */
@Component
public class OpenAiCompatibleEmbeddingClient {

    private final WebClient webClient;
    private final RagEmbeddingProperties props;

    public OpenAiCompatibleEmbeddingClient(WebClient.Builder builder, RagEmbeddingProperties props) {
        this.props = props;
        String normalizedBaseUrl = normalizeBaseUrl(props.getBaseUrl());
        this.webClient = builder
                .baseUrl(normalizedBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .build();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            throw new BizException("rag.embedding.base-url 未配置");
        }
        String normalized = baseUrl.trim();
        // 兼容误配置为 "$https://..." 这种情况，避免 URI 解析报错。
        if (normalized.startsWith("$http://") || normalized.startsWith("$https://")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains("dashscope.aliyuncs.com/compatible-mode") && !normalized.endsWith("/v1")) {
            normalized = normalized + "/v1";
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new BizException("rag.embedding.base-url 非法: " + normalized);
        }
        return normalized;
    }

    /**
     * 将单个文本字符串转换为向量表示（响应式异步方法）
     * 调用兼容OpenAI格式的Embedding API，返回Mono包装的向量列表
     *
     * @param input 待向量化的文本内容
     * @return Mono<List<Double>> 响应式流，包含浮点数向量
     */
    public Mono<List<Double>> embedOne(String input) {
        // 校验输入文本：空值或空白字符串直接返回空向量，避免无效API调用
        if (input == null || input.isBlank()) {
            return Mono.just(List.of());
        }

        // 创建Embedding请求对象，封装API所需的参数结构
        EmbeddingRequest req = new EmbeddingRequest();
        // 设置模型名称（如text-embedding-ada-002），从配置属性中读取
        req.setModel(props.getModelName());
        // 将输入文本包装为列表格式（API要求数组形式，支持批量但此处仅单条）
        req.setInput(List.of(input));

        // 计算超时时间：优先使用配置的timeoutMs，默认15秒
        // Duration对象用于WebClient的timeout操作符，防止请求无限等待
        Duration timeout = Duration.ofMillis(props.getTimeoutMs() == null ? 15000 : props.getTimeoutMs());

        // 构建并执行HTTP POST请求到Embedding API端点
        return webClient.post()
                // 拼接完整URL：baseUrl + "/embeddings"（符合OpenAI API规范）
                .uri("/embeddings")
                // 设置请求头Content-Type为application/json
                .contentType(MediaType.APPLICATION_JSON)
                // 序列化EmbeddingRequest对象为JSON作为请求体
                .bodyValue(req)
                // 发起请求并准备处理响应
                .retrieve()
                // 将响应体反序列化为EmbeddingResponse对象（响应式Mono）
                .bodyToMono(EmbeddingResponse.class)
                // 应用超时控制：超过指定时间未响应则抛出TimeoutException
                .timeout(timeout)
                // 映射转换：从API响应中提取向量数据，并进行校验
                .map(resp -> {
                    // 校验响应对象和数据列表非空：防御性编程，避免NPE
                    if (resp == null || resp.getData() == null || resp.getData().isEmpty()) {
                        throw new BizException("embedding 响应为空");
                    }
                    // 获取第一个文本的向量结果（因为input只传了一个元素）
                    // getEmbedding()返回List<Double>，即高维空间中的向量坐标
                    List<Double> vector = resp.getData().get(0).getEmbedding();
                    // 二次校验向量有效性：确保不是null或空列表
                    if (vector == null || vector.isEmpty()) {
                        throw new BizException("embedding 向量为空");
                    }
                    // 返回提取的向量，完成响应式流的转换
                    return vector;
                });
    }

    @Data
    public static class EmbeddingRequest {
        private String model;
        private List<String> input;
    }

    @Data
    public static class EmbeddingResponse {
        private List<EmbeddingData> data;

        @Data
        public static class EmbeddingData {
            private List<Double> embedding;
        }
    }
}

