package com.pathfinder.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Table(name = "admin_profiles")
@Data
public class AdminProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String team;

    @Column(name = "support_channel", nullable = false)
    private String supportChannel;

    @Column(name = "notes", length = 2000, nullable = false)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDate createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDate updatedAt;

    @PrePersist
    void onCreate() {
        LocalDate today = LocalDate.now();
        if (team == null) {
            team = "";
        }
        if (supportChannel == null) {
            supportChannel = "";
        }
        if (notes == null) {
            notes = "";
        }
        if (createdAt == null) {
            createdAt = today;
        }
        updatedAt = today;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDate.now();
    }
}
