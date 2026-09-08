package com.example.travelhelper_server.service;

import com.example.travelhelper_server.dto.ConversationTurnDTO;
import com.example.travelhelper_server.entity.ChatConversation;
import com.example.travelhelper_server.entity.ConversationMessage;
import com.example.travelhelper_server.entity.User;
import com.example.travelhelper_server.repository.ChatConversationRepository;
import com.example.travelhelper_server.repository.ConversationMessageRepository;
import com.example.travelhelper_server.vo.ConversationDetailVO;
import com.example.travelhelper_server.vo.ConversationMessageVO;
import com.example.travelhelper_server.vo.ConversationSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ChatConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final AuthService authService;

    @Transactional
    public ConversationSummaryVO create(String username, String requestedTitle) {
        User user = authService.requireUser(username);
        ChatConversation conversation = new ChatConversation();
        conversation.setId(UUID.randomUUID().toString());
        conversation.setUser(user);
        conversation.setTitle(hasText(requestedTitle) ? requestedTitle.strip() : "新对话");
        return summary(conversationRepository.save(conversation));
    }

    /** 校验会话归属、读取同一会话上下文，然后原子追加当前用户消息。 */
    @Transactional
    public PreparedTurn prepareTurn(String username, String conversationId, String message) {
        User user = authService.requireUser(username);
        ChatConversation conversation;
        if (!hasText(conversationId)) {
            conversation = new ChatConversation();
            conversation.setId(UUID.randomUUID().toString());
            conversation.setUser(user);
            conversation.setTitle(toTitle(message));
            conversationRepository.save(conversation);
        } else {
            conversation = conversationRepository.findOwnedForUpdate(conversationId, user.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "对话会话不存在"));
        }

        List<ConversationMessage> recent = new ArrayList<>(
                messageRepository.findTop8ByConversationIdOrderBySequenceNoDesc(conversation.getId()));
        Collections.reverse(recent);
        List<ConversationTurnDTO> context = recent.stream()
                .map(item -> new ConversationTurnDTO(item.getRole(), item.getContent()))
                .toList();

        if (conversation.getNextSequence() == 1 || "新对话".equals(conversation.getTitle())) {
            conversation.setTitle(toTitle(message));
        }
        append(conversation, "user", message.strip(), null);
        return new PreparedTurn(conversation.getId(), context);
    }

    @Transactional
    public void appendAssistant(String username, String conversationId, String content, String detailJson) {
        User user = authService.requireUser(username);
        ChatConversation conversation = conversationRepository.findOwnedForUpdate(conversationId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "对话会话不存在"));
        append(conversation, "assistant", content, detailJson);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryVO> list(String username) {
        User user = authService.requireUser(username);
        return conversationRepository.findNonEmptyByUserIdOrderByUpdatedAtDesc(user.getId())
                .stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetailVO get(String username, String conversationId) {
        User user = authService.requireUser(username);
        ChatConversation conversation = conversationRepository.findByIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "对话会话不存在"));
        List<ConversationMessageVO> messages = messageRepository
                .findAllByConversationIdOrderBySequenceNoAsc(conversationId)
                .stream().map(ConversationMessageVO::from).toList();
        return new ConversationDetailVO(conversation.getId(), conversation.getTitle(),
                conversation.getCreatedAt(), conversation.getUpdatedAt(), messages);
    }

    @Transactional
    public void delete(String username, String conversationId) {
        User user = authService.requireUser(username);
        ChatConversation conversation = conversationRepository.findOwnedForUpdate(conversationId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "对话会话不存在"));
        messageRepository.deleteAllByConversationId(conversationId);
        conversationRepository.delete(conversation);
    }

    private void append(ChatConversation conversation, String role, String content, String detailJson) {
        ConversationMessage message = new ConversationMessage();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setDetailJson(detailJson);
        message.setSequenceNo(conversation.getNextSequence());
        conversation.setNextSequence(conversation.getNextSequence() + 1);
        conversationRepository.save(conversation);
        messageRepository.save(message);
    }

    private ConversationSummaryVO summary(ChatConversation conversation) {
        return new ConversationSummaryVO(conversation.getId(), conversation.getTitle(),
                messageRepository.countByConversationId(conversation.getId()),
                conversation.getCreatedAt(), conversation.getUpdatedAt());
    }

    private String toTitle(String message) {
        String value = message == null ? "新对话" : message.strip().replaceAll("\\s+", " ");
        return value.length() <= 36 ? value : value.substring(0, 36) + "…";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record PreparedTurn(String conversationId, List<ConversationTurnDTO> context) {
    }
}
