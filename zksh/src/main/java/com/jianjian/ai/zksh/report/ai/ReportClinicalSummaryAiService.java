package com.jianjian.ai.zksh.report.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel"
)
public interface ReportClinicalSummaryAiService {

    @SystemMessage(fromResource = "report_clinical_summary_system.txt")
    String summarize(@UserMessage String reportJson);
}

