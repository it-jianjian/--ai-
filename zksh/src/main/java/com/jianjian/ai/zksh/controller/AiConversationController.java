package com.jianjian.ai.zksh.controller;

import com.jianjian.ai.zksh.agent.workflow.AgenticScope;
import com.jianjian.ai.zksh.agent.workflow.SequentialChatWorkflowOrchestrator;
import com.jianjian.ai.zksh.common.ApiResponse;
import com.jianjian.ai.zksh.common.BizException;
import com.jianjian.ai.zksh.domain.dto.AiChatMessageRequestDTO;
import com.jianjian.ai.zksh.domain.dto.CreateAiChatSessionDTO;
import com.jianjian.ai.zksh.domain.dto.UpdateAiChatSessionTitleDTO;
import com.jianjian.ai.zksh.domain.vo.AiChatMessageResponseVO;
import com.jianjian.ai.zksh.domain.vo.AiChatSessionVO;
import com.jianjian.ai.zksh.domain.vo.AiReferenceVO;
import com.jianjian.ai.zksh.domain.vo.AiSessionMessageVO;
import com.jianjian.ai.zksh.rag.service.RagReferenceStore;
import com.jianjian.ai.zksh.service.AiConversationService;
import com.jianjian.ai.zksh.service.ConsultantService;
import com.jianjian.ai.zksh.security.UserContext;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author 渐渐
 * @since 2026-04-14 星期二 17:08:09
 */
@RestController()
@RequestMapping("/api/ai")
public class AiConversationController {

    @Autowired
    private ConsultantService consultantService;

    @Autowired
    private AiConversationService aiConversationService;
    @Autowired
    private RagReferenceStore ragReferenceStore;
    @Autowired
    private SequentialChatWorkflowOrchestrator sequentialChatWorkflowOrchestrator;

    @PostMapping(value = "/chat/sessions/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ApiResponse<AiChatMessageResponseVO>> chat(@PathVariable("sessionId") Long sessionId,
                                                           @Valid @RequestBody AiChatMessageRequestDTO request) {
        Long userId = currentUserId();
        //构建会话id存入会话记忆库:“用户id+会话id”作为key
        String memoryId = buildMemoryId(sessionId);
        //保存用户数据入库
        aiConversationService.saveUserMessage(userId, sessionId, request.content());
        AgenticScope scope = sequentialChatWorkflowOrchestrator.run(userId, sessionId, memoryId, request.content());
        //先生成流式阶段的临时消息id，结束后替换为数据库id
        String tempMessageId = "m_tmp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        //构建ai回复的完整内容
        StringBuilder fullAnswer = new StringBuilder();
        //统计token数量
        AtomicInteger tokenCounter = new AtomicInteger();

        Flux<ApiResponse<AiChatMessageResponseVO>> stream = consultantService.chat(memoryId, scope.getQueryForAnswer())
        //.chat(xx)返回 Flux<String>，可以理解成“很多个分片的回答流”。每来一个 chunk，就会进入 .map(...)。把分片拼成完整的回答。
                .map(chunk -> {
                    fullAnswer.append(chunk);
                    tokenCounter.addAndGet(chunk.length());
                    AiChatMessageResponseVO data = new AiChatMessageResponseVO(
                            tempMessageId,
                            chunk,
                            null,
                            List.of(),
                            tokenCounter.get()
                    );
                    return ApiResponse.ok(data); //返回一条包含当前分片内容的响应给前端
                });

        Mono<ApiResponse<AiChatMessageResponseVO>> done = Mono.fromSupplier(() -> {
            String answer = fullAnswer.toString();
            String riskLevel = detectRiskLevel(answer);
            List<AiReferenceVO> references = ragReferenceStore.consume(memoryId);
            Long messageId = aiConversationService.saveAssistantMessage(
                    userId,
                    sessionId,
                    answer,
                    riskLevel,
                    tokenCounter.get()
            );
            AiChatMessageResponseVO data = new AiChatMessageResponseVO(
                    "m_" + messageId,
                    answer,
                    riskLevel,
                    references,
                    tokenCounter.get()
            );
            return ApiResponse.ok(data);
            /*表示“流结束后，再生成一条最终消息”。
这条最终消息里：answer 是完整答案 fullAnswer.toString()riskLevel 已计算好tokens 是累计值*/
        });
        return stream.concatWith(done);
        /**先把所有流式片段发出去（stream）
再发最后一条汇总事件（done）
所以前端会收到：chunk1 -> chunk2 -> ... -> final */
    }

    @PostMapping("/chat/sessions")
    public ApiResponse<AiChatSessionVO> createSession(@Valid @RequestBody CreateAiChatSessionDTO dto) {
        return ApiResponse.ok(aiConversationService.createSession(currentUserId(), dto));
    }

    @GetMapping("/chat/sessions")
    public ApiResponse<List<AiChatSessionVO>> listSessions() {
        return ApiResponse.ok(aiConversationService.listSessions(currentUserId()));
    }

    @GetMapping("/chat/sessions/{sessionId}")
    public ApiResponse<AiChatSessionVO> getSession(@PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(aiConversationService.getSession(currentUserId(), sessionId));
    }

    @PutMapping("/chat/sessions/{sessionId}/title")
    public ApiResponse<AiChatSessionVO> renameSession(@PathVariable("sessionId") Long sessionId,
                                                      @Valid @RequestBody UpdateAiChatSessionTitleDTO dto) {
        return ApiResponse.ok(aiConversationService.renameSession(currentUserId(), sessionId, dto.title()));
    }

    @DeleteMapping("/chat/sessions/{sessionId}")
    public ApiResponse<Boolean> deleteSession(@PathVariable("sessionId") Long sessionId) {
        aiConversationService.deleteSession(currentUserId(), sessionId);
        return ApiResponse.ok(true);
    }

    @GetMapping("/chat/sessions/{sessionId}/messages")
    public ApiResponse<List<AiSessionMessageVO>> listMessages(@PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(aiConversationService.listMessages(currentUserId(), sessionId));
    }

    private String buildMemoryId(Long sessionId) {
        Long userId = currentUserId();
        return userId + ":" + sessionId;
    }

    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException("请先登录后再操作");
        }
        return userId;
    }

    private String detectRiskLevel(String answer) {
        String content = answer == null ? "" : answer.toLowerCase();
        if (content.contains("急诊") || content.contains("立即就医") || content.contains("胸痛") || content.contains("呼吸困难")) {
            return "HIGH";
        }
        if (content.contains("建议") || content.contains("检查") || content.contains("观察")) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
