package com.example.travelhelper_server.repository;

import com.example.travelhelper_server.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {
    List<ConversationMessage> findAllByConversationIdOrderBySequenceNoAsc(String conversationId);
    List<ConversationMessage> findTop8ByConversationIdOrderBySequenceNoDesc(String conversationId);
    long countByConversationId(String conversationId);
    void deleteAllByConversationId(String conversationId);
}
