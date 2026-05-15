package com.jianjian.ai.zksh.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel"
)
public interface SessionTopicAiService {

    @SystemMessage("""
            你是一个“会话主题提炼器”，负责把用户的一句话问题提炼成会话标题。
            规则：
            - 只输出一个短语作为标题，不要解释，不要换行
            - 不要给出诊断结论，不要包含治疗方案
            - 不要包含引号、书名号等符号
            - 优先提炼“症状/检查/用药/指标/报告解读”等主题词
            - 中文为主，长度尽量短（<= 20字更好）
            """)
    String topic(@UserMessage String question);
}

