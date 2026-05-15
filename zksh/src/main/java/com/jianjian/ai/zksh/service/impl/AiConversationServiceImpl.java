package com.jianjian.ai.zksh.service.impl;

import com.jianjian.ai.zksh.common.BizException;
import com.jianjian.ai.zksh.domain.dto.CreateAiChatSessionDTO;
import com.jianjian.ai.zksh.domain.entity.AiChatMessageEntity;
import com.jianjian.ai.zksh.domain.entity.AiChatSessionEntity;
import com.jianjian.ai.zksh.domain.vo.AiChatSessionVO;
import com.jianjian.ai.zksh.domain.vo.AiSessionMessageVO;
import com.jianjian.ai.zksh.mapper.AiChatMessageMapper;
import com.jianjian.ai.zksh.mapper.AiChatSessionMapper;
import com.jianjian.ai.zksh.service.AiConversationService;
import com.jianjian.ai.zksh.service.SessionTopicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AiConversationServiceImpl implements AiConversationService {

    @Autowired
    private AiChatSessionMapper aiChatSessionMapper;

    @Autowired
    private AiChatMessageMapper aiChatMessageMapper;

    @Autowired
    private SessionTopicService sessionTopicService;

    @Override
    public AiChatSessionVO createSession(Long userId, CreateAiChatSessionDTO dto) {
        AiChatSessionEntity entity = new AiChatSessionEntity();
        entity.setUserId(userId);
        entity.setTitle(StringUtils.hasText(dto.title()) ? dto.title().trim() : "新会话");
        aiChatSessionMapper.insert(entity);
        AiChatSessionEntity created = aiChatSessionMapper.selectByIdAndUserId(entity.getId(), userId);
        return toSessionVO(created);
    }

    @Override
    public List<AiChatSessionVO> listSessions(Long userId) {
        return aiChatSessionMapper.selectByUserId(userId).stream()
                .map(this::toSessionVO)
                .toList();
    }

    @Override
    public AiChatSessionVO getSession(Long userId, Long sessionId) {
        return toSessionVO(requireSession(userId, sessionId));
    }

    @Override
    public List<AiSessionMessageVO> listMessages(Long userId, Long sessionId) {
        requireSession(userId, sessionId);
        return aiChatMessageMapper.selectBySessionId(sessionId, userId).stream()
                .map(this::toMessageVO)
                .toList();
    }

    @Override
    public AiChatSessionVO renameSession(Long userId, Long sessionId, String title) {
        AiChatSessionEntity session = requireSession(userId, sessionId);
        String normalizedTitle = normalizeTitle(title);
        if (Objects.equals(session.getTitle(), normalizedTitle)) {
            return toSessionVO(session);
        }
        aiChatSessionMapper.updateTitle(sessionId, userId, normalizedTitle);
        return toSessionVO(requireSession(userId, sessionId));
    }

    @Override
    public void deleteSession(Long userId, Long sessionId) {
        requireSession(userId, sessionId);
        aiChatMessageMapper.deleteBySessionId(sessionId, userId);
        aiChatSessionMapper.softDelete(sessionId, userId);
    }

    @Override
    public void refineSessionTopicByQuestion(Long userId, Long sessionId, String question) {
        // 步骤1：验证会话存在性和归属权
        // requireSession() 会查询数据库获取会话实体，如果会话不存在或不属于当前用户则抛出异常
        // 这是安全校验的关键步骤，防止用户操作他人的会话数据
        AiChatSessionEntity session = requireSession(userId, sessionId);

        // 步骤2：检查会话标题是否为默认标题
        // isDefaultTitle() 判断标题是否是系统生成的默认值（如"新对话"、"New Chat"等）
        // 如果用户已经自定义了标题，说明用户对标题满意，不需要再自动优化
        // 这个判断保护了用户的个性化设置，避免覆盖用户手动修改的标题
        if (!isDefaultTitle(session.getTitle())) {
            return;  // 已有自定义标题，直接退出，不做任何修改
        }

        // 步骤3：统计当前会话的消息数量
        // countBySessionId() 查询数据库中该会话下的消息总数
        // 传入 userId 是为了双重校验，确保只统计当前用户的消息
        int messageCount = aiChatMessageMapper.countBySessionId(sessionId, userId);

        // 步骤4：判断是否超过第一条消息
        // 标题优化只在会话的第一条消息时执行一次
        // 原因：
        //   1. 避免每次发消息都触发标题更新，减少数据库写操作
        //   2. 第一条消息通常最能代表会话的核心主题
        //   3. 后续消息可能偏离初始主题，频繁更新标题会造成用户体验混乱
        if (messageCount > 1) {
            return;  // 已有超过一条消息，说明之前已经优化过或跳过优化，不再重复执行
        }

        // 步骤5：尝试从问题中提取关键主题
        // extractTopicOrNull() 调用 AI 服务（通常是 LLM）分析用户问题，提取核心主题词
        // 例如："我最近总是失眠怎么办？" → "失眠问题咨询"
        // 如果提取成功，返回主题字符串；如果失败或无法提取，返回 null
        String topic = sessionTopicService.extractTopicOrNull(question);

        // 步骤6：降级策略 - 如果主题提取失败，尝试生成问题摘要
        // StringUtils.hasText() 检查 topic 是否为 null、空字符串或纯空白
        // 如果提取的主题无效，使用备用方案：对问题进行摘要总结
        if (!StringUtils.hasText(topic)) {
            // summarizeQuestion() 调用 AI 摘要服务，生成问题的简短概述
            // 例如："我最近总是失眠，晚上翻来覆去睡不着，白天精神很差，这种情况持续了一个月..."
            //       → "长期失眠问题咨询"
            // 这种方式比主题提取更通用，但可能不如主题提取精准
            topic = summarizeQuestion(question);
        }

        // 步骤7：最终有效性验证
        // 经过主题提取和摘要总结两种方式后，如果仍然没有得到有效标题，则放弃优化
        // 可能的原因：
        //   1. 用户输入的是无意义字符（如"？？？"、"..."）
        //   2. AI 服务调用失败或超时
        //   3. 问题过于简短或模糊，无法提取有效信息
        // 此时保留默认标题，等待用户后续交互或手动修改
        if (!StringUtils.hasText(topic)) {
            return;  // 无法生成有效标题，保持默认标题不变
        }

        // 步骤8：持久化更新会话标题
        // updateTitle() 执行 SQL UPDATE 语句，将会话标题更新为优化后的主题
        // 传入 userId 是为了在 SQL 层面再次校验归属权（WHERE user_id = ? AND id = ?）
        // 这是一种防御性编程实践，即使前面校验通过，数据库层面也要确保安全
        aiChatSessionMapper.updateTitle(sessionId, userId, topic);

        // 方法结束，标题优化完成
        // 注意：这是一个 void 方法，不返回任何值
        // 调用者不需要关心优化是否成功，系统已经做了最大努力尝试
    }
    @Override
    public void saveUserMessage(Long userId, Long sessionId, String content) {
        requireSession(userId, sessionId);
        AiChatMessageEntity entity = new AiChatMessageEntity();
        entity.setSessionId(sessionId);
        entity.setUserId(userId);
        entity.setRole("USER");
        entity.setContent(content);
        entity.setRiskLevel(null);
        entity.setTokens(null);
        aiChatMessageMapper.insert(entity);
        aiChatSessionMapper.touchById(sessionId, LocalDateTime.now());
    }

    @Override
    public Long saveAssistantMessage(Long userId, Long sessionId, String content, String riskLevel, Integer tokens) {
        requireSession(userId, sessionId);
        AiChatMessageEntity entity = new AiChatMessageEntity();
        entity.setSessionId(sessionId);
        entity.setUserId(userId);
        entity.setRole("ASSISTANT");
        entity.setContent(content);
        entity.setRiskLevel(riskLevel);
        entity.setTokens(tokens);
        aiChatMessageMapper.insert(entity);
        aiChatSessionMapper.touchById(sessionId, LocalDateTime.now());
        return entity.getId();
    }

    private AiChatSessionEntity requireSession(Long userId, Long sessionId) {
        AiChatSessionEntity session = aiChatSessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            throw new BizException("会话不存在或无访问权限");
        }
        return session;
    }

    private AiChatSessionVO toSessionVO(AiChatSessionEntity entity) {
        return new AiChatSessionVO(
                entity.getId(),
                entity.getTitle(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLastMessageAt()
        );
    }

    private AiSessionMessageVO toMessageVO(AiChatMessageEntity message) {
        return new AiSessionMessageVO(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getRiskLevel(),
                message.getTokens(),
                message.getCreatedAt()
        );
    }

    private String normalizeTitle(String title) {
        return StringUtils.hasText(title) ? title.trim() : "新会话";
    }

    private boolean isDefaultTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return true;
        }
        String normalized = title.trim();
        return "新会话".equals(normalized) || normalized.startsWith("新会话-");
    }

    private String summarizeQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            return "新会话";
        }
        String normalized = question
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("[，。！？；,.!?;:：]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (!StringUtils.hasText(normalized)) {
            return "新会话";
        }
        List<String> tokens = Arrays.stream(normalized.split(" "))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        String title;
        if (tokens.size() >= 2) {
            title = tokens.stream().limit(3).collect(Collectors.joining(" "));
        } else {
            title = normalized;
        }
        title = title.length() > 24 ? title.substring(0, 24) : title;
        return title.trim();
    }
}
