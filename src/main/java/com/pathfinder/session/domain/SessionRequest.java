package com.pathfinder.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "session_requests")
@Getter
@Setter
public class SessionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String seekerEmail;

    @Column(nullable = false)
    private String mentorEmail;

    @Column(nullable = false)
    private String mentorName;

    @Column(nullable = false)
    private String slotTime;

    @Column(nullable = false)
    private String sessionType;

    @Column(nullable = false, length = 500)
    private String objective;

    @Column(length = 1000)
    private String bookingNotes;

    @Column(length = 1000)
    private String mentorNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(nullable = false)
    private boolean paymentCompleted;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (status == null) {
            status = SessionStatus.REQUESTED;
        }
        createdAt = LocalDateTime.now();
    }
}
