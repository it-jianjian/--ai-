package com.jianjian.ai.zksh.rag.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TokenChunker {

    /**
     * 轻量切片器（V1）。
     *
     * <p>目标是“工程可用”而不是“token 精确对齐某个模型”：
     * - 汉字按单字计 1 token（粗略）
     * - 英文数字按连续词计 1 token
     * - 其它符号单独计</p>
     *
     * <p>后续如果要更精确，可替换为 tiktoken/模型官方 tokenizer，并保持接口不变。</p>
     */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]|[A-Za-z0-9]+|[^\\s]");

    public List<String> chunk(String text, int targetTokens, int overlapTokens, int minTokens, int maxTokens) {
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return List.of();
        }

        int step = Math.max(1, targetTokens - overlapTokens);
        List<String> chunks = new ArrayList<>();

        for (int start = 0; start < tokens.size(); start += step) {
            int end = Math.min(tokens.size(), start + targetTokens);
            // 确保不超过 maxTokens
            if (end - start > maxTokens) {
                end = start + maxTokens;
            }
            // 末尾不足 minTokens 时，合并到最后一块（避免太碎）
            if (!chunks.isEmpty() && (tokens.size() - start) < minTokens) {
                break;
            }
            chunks.add(join(tokens, start, end));
            if (end >= tokens.size()) {
                break;
            }
        }

        // 把尾巴补齐到最后一块（避免出现极短的“尾块”）
        if (chunks.isEmpty() && tokens.size() > 0) {
            chunks.add(join(tokens, 0, tokens.size()));
        }
        return chunks;
    }

    public int countTokens(String text) {
        return tokenize(text).size();
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        Matcher m = TOKEN_PATTERN.matcher(normalized);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    private String join(List<String> tokens, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append(tokens.get(i));
            // 英文单词之间加空格，避免粘连；中文保持紧凑
            if (i + 1 < end && needsSpace(tokens.get(i), tokens.get(i + 1))) {
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }

    private boolean needsSpace(String a, String b) {
        boolean aWord = a.matches("[A-Za-z0-9]+");
        boolean bWord = b.matches("[A-Za-z0-9]+");
        return aWord && bWord;
    }
}

