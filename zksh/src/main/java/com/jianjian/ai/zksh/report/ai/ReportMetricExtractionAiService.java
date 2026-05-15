package com.jianjian.ai.zksh.report.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel"
)
public interface ReportMetricExtractionAiService {

    @SystemMessage(fromResource = "report_extractor_system.txt")
    String extract(@UserMessage String ocrText);
}

