package com.jianjian.ai.zksh.service;

import com.jianjian.ai.zksh.domain.dto.CreateAiChatSessionDTO;
import com.jianjian.ai.zksh.domain.vo.AiChatSessionVO;
import com.jianjian.ai.zksh.domain.vo.AiSessionMessageVO;

import java.util.List;

public interface AiConversationService {
    AiChatSessionVO createSession(Long userId, CreateAiChatSessionDTO dto);

    List<AiChatSessionVO> listSessions(Long userId);

    AiChatSessionVO getSession(Long userId, Long sessionId);

    List<AiSessionMessageVO> listMessages(Long userId, Long sessionId);

    AiChatSessionVO renameSession(Long userId, Long sessionId, String title);

    void deleteSession(Long userId, Long sessionId);

    void refineSessionTopicByQuestion(Long userId, Long sessionId, String question);

    void saveUserMessage(Long userId, Long sessionId, String content);

    Long saveAssistantMessage(Long userId, Long sessionId, String content, String riskLevel, Integer tokens);
}
