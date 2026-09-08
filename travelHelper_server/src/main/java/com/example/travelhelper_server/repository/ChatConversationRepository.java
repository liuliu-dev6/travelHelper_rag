package com.example.travelhelper_server.repository;

import com.example.travelhelper_server.entity.ChatConversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, String> {
    @Query("select c from ChatConversation c " +
            "where c.user.id=:userId and exists (" +
            "select m.id from ConversationMessage m where m.conversation.id=c.id) " +
            "order by c.updatedAt desc")
    List<ChatConversation> findNonEmptyByUserIdOrderByUpdatedAtDesc(@Param("userId") Long userId);
    Optional<ChatConversation> findByIdAndUserId(String id, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChatConversation c where c.id=:id and c.user.id=:userId")
    Optional<ChatConversation> findOwnedForUpdate(@Param("id") String id, @Param("userId") Long userId);
}
