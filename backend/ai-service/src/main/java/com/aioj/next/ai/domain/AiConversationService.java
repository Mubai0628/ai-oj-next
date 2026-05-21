package com.aioj.next.ai.domain;

import com.aioj.next.ai.persistence.entity.AiConversationEntity;
import com.aioj.next.ai.persistence.entity.AiMessageEntity;
import com.aioj.next.ai.persistence.mapper.AiConversationMapper;
import com.aioj.next.ai.persistence.mapper.AiMessageMapper;
import com.aioj.next.common.api.PageResponse;
import com.aioj.next.common.error.DomainException;
import com.aioj.next.common.error.ErrorCode;
import com.aioj.next.contract.ai.AiChatMessageResponse;
import com.aioj.next.contract.ai.AiChatRequest;
import com.aioj.next.contract.ai.AiConversationResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class AiConversationService {
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;

    public AiConversationService(AiConversationMapper conversationMapper, AiMessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    @Transactional
    public AiConversationEntity resolveForWrite(Long userId, AiChatRequest request) {
        if (request.conversationId() == null || request.conversationId().isBlank()) {
            return create(userId, request.problemId(), request.message());
        }
        AiConversationEntity conversation = conversationMapper.selectById(request.conversationId());
        if (conversation == null) {
            return create(userId, request.problemId(), request.message());
        }
        ensureOwner(conversation, userId);
        return conversation;
    }

    public void ensureOwner(String conversationId, Long userId) {
        AiConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new DomainException(ErrorCode.NOT_FOUND, "AI conversation not found");
        }
        ensureOwner(conversation, userId);
    }

    @Transactional
    public AiChatMessageResponse appendMessage(String conversationId, Long userId, String role, String content, String model) {
        LocalDateTime now = LocalDateTime.now();
        AiMessageEntity message = new AiMessageEntity();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setModel(model);
        message.setCreatedAt(now);
        messageMapper.insert(message);

        AiConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation != null) {
            conversation.setUpdatedAt(now);
            conversationMapper.updateById(conversation);
        }
        return toMessageResponse(message);
    }

    public PageResponse<AiConversationResponse> list(Long userId, long page, long pageSize) {
        long current = Math.max(1, page);
        long size = Math.min(Math.max(1, pageSize), 100);
        long offset = (current - 1) * size;
        QueryWrapper<AiConversationEntity> countQuery = new QueryWrapper<AiConversationEntity>()
                .eq("user_id", userId);
        long total = conversationMapper.selectCount(countQuery);
        List<AiConversationResponse> records = conversationMapper.selectList(new QueryWrapper<AiConversationEntity>()
                        .eq("user_id", userId)
                        .orderByDesc("updated_at")
                        .last("LIMIT " + size + " OFFSET " + offset))
                .stream()
                .map(this::toConversationResponse)
                .toList();
        return new PageResponse<>(records, total, current, size);
    }

    public List<AiChatMessageResponse> messages(Long userId, String conversationId) {
        ensureOwner(conversationId, userId);
        return messageMapper.selectList(new QueryWrapper<AiMessageEntity>()
                        .eq("conversation_id", conversationId)
                        .orderByAsc("created_at"))
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional
    public void delete(Long userId, String conversationId) {
        ensureOwner(conversationId, userId);
        messageMapper.delete(new QueryWrapper<AiMessageEntity>().eq("conversation_id", conversationId));
        conversationMapper.deleteById(conversationId);
    }

    private AiConversationEntity create(Long userId, Long problemId, String message) {
        LocalDateTime now = LocalDateTime.now();
        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setId(UUID.randomUUID().toString());
        conversation.setUserId(userId);
        conversation.setProblemId(problemId);
        conversation.setTitle(titleFrom(message));
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);
        return conversation;
    }

    private void ensureOwner(AiConversationEntity conversation, Long userId) {
        if (!userId.equals(conversation.getUserId())) {
            throw new DomainException(ErrorCode.FORBIDDEN, "AI conversation belongs to another user");
        }
    }

    private AiConversationResponse toConversationResponse(AiConversationEntity conversation) {
        return new AiConversationResponse(
                conversation.getId(),
                conversation.getProblemId(),
                conversation.getTitle(),
                conversation.getCreatedAt().atZone(ZONE).toInstant(),
                conversation.getUpdatedAt().atZone(ZONE).toInstant()
        );
    }

    private AiChatMessageResponse toMessageResponse(AiMessageEntity message) {
        return new AiChatMessageResponse(
                message.getConversationId(),
                message.getRole(),
                message.getContent(),
                message.getModel(),
                message.getCreatedAt().atZone(ZONE).toInstant()
        );
    }

    private String titleFrom(String message) {
        if (message == null || message.isBlank()) {
            return "New AI conversation";
        }
        String normalized = message.strip().replaceAll("\\s+", " ");
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }
}
