package com.jianjian.ai.zksh.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jianjian.ai.zksh.common.BizException;
import com.jianjian.ai.zksh.report.config.ReportProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * 阿里云市场 - 通用 OCR（ocr_general）客户端（MVP）。
 *
 * <p>接口参考：/api/predict/ocr_general。当前实现仅使用 APPCODE（简单身份认证）。</p>
 */
@Service
public class AliyunGeneralOcrClient {

    private final WebClient webClient;
    private final ReportProperties props;
    private final ObjectMapper objectMapper;

    public AliyunGeneralOcrClient(ReportProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(props.getOcr().getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Mono<OcrResult> recognize(byte[] bytes) {
        return recognize(bytes, "image/jpeg");
    }

    public Mono<OcrResult> recognize(byte[] bytes, String mimeType) {
        if (bytes == null || bytes.length == 0) {
            return Mono.error(new BizException("OCR 失败：图片为空"));
        }
        if (props.getOcr().getAppcode() == null || props.getOcr().getAppcode().isBlank()) {
            return Mono.error(new BizException("OCR 未配置：请设置 app.report.ocr.appcode"));
        }
        String base64 = Base64.getEncoder().encodeToString(bytes);
        String type = (mimeType == null || mimeType.isBlank()) ? "image/jpeg" : mimeType;
        String data = "data:" + type + ";base64," + base64;
        Map<String, Object> body = Map.of(
                "image", data,
                "configure", Map.of(
                        "output_prob", false,
                        "output_keypoints", false,
                        "skip_detection", false,
                        "dir_assure", false
                )
        );
        return webClient.post()
                .uri(props.getOcr().getPath())
                .header(HttpHeaders.AUTHORIZATION, "APPCODE " + props.getOcr().getAppcode())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getOcr().getTimeoutMs() == null ? 15000 : props.getOcr().getTimeoutMs()))
                .map(raw -> new OcrResult(extractTextOrFallback(raw), raw))
                .onErrorMap(WebClientResponseException.class, e ->
                        new BizException("OCR 调用失败: HTTP " + e.getStatusCode().value() + " " + safeBody(e)));
    }

    private String extractTextOrFallback(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            // 不同供应商/版本字段可能不同，这里做尽量兼容：
            // - 常见：ret / result / prism_wordsInfo / words_result ...
            // 先尽量把所有可见文字拼出来，供后续 LLM 抽取。
            StringBuilder sb = new StringBuilder();

            JsonNode ret = root.get("ret");
            if (ret != null && ret.isArray()) {
                for (JsonNode n : ret) {
                    JsonNode word = n.get("word");
                    if (word != null && !word.asText().isBlank()) {
                        sb.append(word.asText()).append("\n");
                    }
                }
            }

            JsonNode wordsResult = root.get("words_result");
            if (wordsResult != null && wordsResult.isArray()) {
                for (JsonNode n : wordsResult) {
                    JsonNode words = n.get("words");
                    if (words != null && !words.asText().isBlank()) {
                        sb.append(words.asText()).append("\n");
                    }
                }
            }

            String text = sb.toString().trim();
            return text.isBlank() ? rawJson : text;
        } catch (Exception e) {
            return rawJson;
        }
    }

    private String safeBody(WebClientResponseException e) {
        try {
            byte[] b = e.getResponseBodyAsByteArray();
            if (b == null || b.length == 0) return "";
            String s = new String(b, StandardCharsets.UTF_8);
            return s.length() > 300 ? s.substring(0, 300) : s;
        } catch (Exception ex) {
            return "";
        }
    }

    public record OcrResult(String text, String rawJson) {
    }
}

