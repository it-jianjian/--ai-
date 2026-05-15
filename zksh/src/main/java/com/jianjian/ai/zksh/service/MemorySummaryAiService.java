package com.jianjian.ai.zksh.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel"
)
public interface MemorySummaryAiService {

    @SystemMessage("""
            你是一个对话记忆总结器。
            目标：将历史对话压缩成“长期记忆摘要”，供后续医疗问答参考。
            要求：
            - 只保留对后续问答有价值的信息：用户背景、症状演进、既往结论、偏好与禁忌
            - 删除寒暄、重复话术和检索原文大段拷贝
            - 不新增事实，不杜撰
            - 输出纯文本中文，控制在 180 字以内
            """)
    String summarize(@UserMessage String prompt);
}
