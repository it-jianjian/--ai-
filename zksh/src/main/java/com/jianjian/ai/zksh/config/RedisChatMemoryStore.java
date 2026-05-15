package com.jianjian.ai.zksh.config;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import com.jianjian.ai.zksh.service.MemorySummaryService;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;
/**
 * @author 渐渐
 * @since 2026-04-14 星期二 17:54:14
 */
@Repository
public class RedisChatMemoryStore implements ChatMemoryStore {
    private static final String RAG_CONTEXT_MARKER = "\n\nAnswer using the following information:\n";
    private static final String SUMMARY_PREFIX = "【会话摘要】";

    //注入RedisTemplate
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private MemorySummaryService memorySummaryService;
    @Autowired
    private AiMemorySummaryProperties memorySummaryProperties;
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        //获取会话消息
        String json = redisTemplate.opsForValue().get(memoryId.toString());
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        //把json字符串转化成List<ChatMessage>
        List<ChatMessage> list = ChatMessageDeserializer.messagesFromJson(json);
        return list;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        //更新会话消息
        List<ChatMessage> normalizedMessages = normalizeMessages(list);
        List<ChatMessage> compactMessages = compactMessages(normalizedMessages);
        //1.把list转换成json数据
        String json = ChatMessageSerializer.messagesToJson(compactMessages);
        //2.把json数据存储到redis中
        redisTemplate.opsForValue().set(memoryId.toString(),json, Duration.ofDays(1));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        //删除会话消息
        redisTemplate.delete(memoryId.toString());
    }

    /**
     * LangChain4j 在检索增强后会把“问题 + 检索片段”一起写入记忆。
     * 为避免上下文污染，这里只保留原始问题，不把检索注入内容持久化到 Redis。
     */
    private List<ChatMessage> normalizeMessages(List<ChatMessage> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> result = new ArrayList<>(source.size());
        for (ChatMessage message : source) {
            if (message instanceof UserMessage userMessage) {
                String text = userMessage.singleText();
                String clean = stripRagContext(text);
                result.add(UserMessage.from(clean));
                continue;
            }
            if (message instanceof AiMessage aiMessage) {
                result.add(AiMessage.from(aiMessage.text()));
                continue;
            }
            if (message instanceof SystemMessage systemMessage) {
                result.add(SystemMessage.from(systemMessage.text()));
                continue;
            }
            result.add(message);
        }
        return result;
    }

    private String stripRagContext(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        int markerIndex = text.indexOf(RAG_CONTEXT_MARKER);
        if (markerIndex < 0) {
            return text;
        }
        return text.substring(0, markerIndex).trim();
    }

    private List<ChatMessage> compactMessages(List<ChatMessage> source) {
        if (source == null || source.isEmpty() || !memorySummaryProperties.isEnabled()) {
            return source;
        }
        if (!(source.get(source.size() - 1) instanceof AiMessage)) {
            return source;
        }

        SystemMessage primarySystem = null;
        String existingSummary = null;
        List<ChatMessage> conversation = new ArrayList<>();
        for (ChatMessage message : source) {
            if (message instanceof SystemMessage systemMessage) {
                String txt = systemMessage.text();
                if (StringUtils.hasText(txt) && txt.startsWith(SUMMARY_PREFIX)) {
                    existingSummary = txt.substring(SUMMARY_PREFIX.length()).trim();
                    continue;
                }
                if (primarySystem == null) {
                    primarySystem = systemMessage;
                }
                continue;
            }
            conversation.add(message);
        }

        int keepRecent = Math.max(4, memorySummaryProperties.getKeepRecentMessages());
        if (conversation.size() <= keepRecent) {
            return rebuildMessages(primarySystem, existingSummary, conversation);
        }

        List<ChatMessage> older = new ArrayList<>(conversation.subList(0, conversation.size() - keepRecent));
        List<ChatMessage> recent = new ArrayList<>(conversation.subList(conversation.size() - keepRecent, conversation.size()));
        String historyText = toHistoryText(older);
        String summary = memorySummaryService.summarize(existingSummary, historyText);
        return rebuildMessages(primarySystem, summary, recent);
    }

    private List<ChatMessage> rebuildMessages(SystemMessage primarySystem, String summary, List<ChatMessage> conversation) {
        List<ChatMessage> result = new ArrayList<>();
        if (primarySystem != null) {
            result.add(primarySystem);
        }
        if (StringUtils.hasText(summary)) {
            result.add(SystemMessage.from(SUMMARY_PREFIX + summary));
        }
        result.addAll(conversation);
        return result;
    }

    private String toHistoryText(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage userMessage) {
                sb.append("用户: ").append(userMessage.singleText());
            } else if (message instanceof AiMessage aiMessage) {
                sb.append("助手: ").append(aiMessage.text());
            } else if (message instanceof SystemMessage systemMessage) {
                sb.append("系统: ").append(systemMessage.text());
            } else {
                sb.append(message.toString());
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }
}