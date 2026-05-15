package com.jianjian.ai.zksh.config;

import com.jianjian.ai.zksh.service.ConsultantService;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author 渐渐
 * @since 2026-04-14 星期二 17:23:55
 */
@Configuration
public class CommonConfig {
    @Autowired
    private OpenAiChatModel model;
    @Autowired
    private RedisChatMemoryStore store;
   /* @Bean
    public ConsultantService consultantService() {
        ConsultantService cs = AiServices.builder(ConsultantService.class)
                .chatModel(model)//设置对话时使用的模型对象
                .build();
        return cs;
    }*/
    //定义会话对象
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)//最大保存的会话记录数量
                .build();
    }
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        //会话记忆的提供者，为每一个会话记忆提供一个会话记忆对象 存储相关会话
        ChatMemoryProvider chatMemoryProvider = new ChatMemoryProvider() {
            @Override
            public ChatMemory get(Object memoryId) {
                return MessageWindowChatMemory.builder()
                        .id(memoryId)//id值
                        .maxMessages(20)//最大会话记录数量
                        .chatMemoryStore(store)//会话存储库
                        .build();
            }
        };
        return chatMemoryProvider;
    }
}
