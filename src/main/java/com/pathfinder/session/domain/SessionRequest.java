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
    private String menteeEmail;

    @Column(name = "mentee_user_id")
    private Long menteeUserId;

    @Column(nullable = false)
    private String mentorEmail;

    @Column(name = "mentor_user_id")
    private Long mentorUserId;

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

    @Column(name = "free_session_requested", nullable = false)
    private boolean freeSessionRequested;

    @Column(name = "quoted_amount_cents")
    private Integer quotedAmountCents;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (status == null) {
            status = SessionStatus.REQUESTED;
        }
        if (quotedAmountCents == null) {
            quotedAmountCents = 0;
        }
        createdAt = LocalDateTime.now();
    }
}
