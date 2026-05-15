package com.jianjian.ai.zksh.rag.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel"
)
public interface QueryRewriteAiService {

    @SystemMessage("""
            你是一个“检索查询改写器（Query Rewriter）”，负责把用户口语问题改写为更适合检索的查询。
            要求：
            - 不要新增诊断结论，不要新增用户未提到的症状与检查结果
            - 保留用户核心意图，补全医学术语/别名/缩写
            - 输出必须是严格 JSON（不要 Markdown，不要代码块，不要多余文字）
            - JSON schema：
              {"rewrittenQuery": "xxx", "keywords": ["k1","k2","k3"]}
            - rewrittenQuery：一句话即可，尽量短（不超过 120 字），不要换行
            - keywords：3~8 个关键词，中文为主
            """)
    String rewrite(@UserMessage String question);
}

