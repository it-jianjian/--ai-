package com.jianjian.ai.zksh.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

/**
 * @author 渐渐
 * @since 2026-04-14 星期二 17:24:53
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel",
        streamingChatModel = "openAiStreamingChatModel",
        chatMemoryProvider  = "chatMemoryProvider", //会话记忆提供对象
        contentRetriever = "contentRetriever", // 内容检索器：进行检索增强（Qdrant + MySQL）
        tools = {"healthAiTools"} // Function Calling 工具集：健康摘要/记录/通知等只读工具

)
//声明封装聊天方法的接口
public interface ConsultantService {
    @SystemMessage(fromResource = "system.txt")
    Flux<String> chat(@MemoryId String memoryId, @UserMessage String message);


}
