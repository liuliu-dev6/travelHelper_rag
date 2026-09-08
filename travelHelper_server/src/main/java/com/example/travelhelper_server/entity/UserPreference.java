package com.example.travelhelper_server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_preferences", uniqueConstraints =
        @UniqueConstraint(name = "uk_user_preferences_user", columnNames = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class UserPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "preferred_themes", nullable = false, length = 1000)
    private String preferredThemes = "[]";

    @Column(name = "avoided_themes", nullable = false, length = 1000)
    private String avoidedThemes = "[]";

    @Column(nullable = false, length = 500)
    private String companions = "[]";

    @Column(name = "max_budget")
    private Integer maxBudget;

    @Column(name = "walking_ability", length = 20)
    private String walkingAbility;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
