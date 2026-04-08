package com.pathfinder.mentor.domain;

import com.pathfinder.auth.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "mentor_profiles")
@Getter
@Setter
@NoArgsConstructor
public class MentorProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT")
    private String expertise;

    @Column(name = "hourly_rate_cents", nullable = false)
    private Integer hourlyRateCents;

    @Column(name = "current_company", length = 120)
    private String currentCompany;

    @Column(name = "current_title", length = 120)
    private String currentTitle;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "sessions_completed", nullable = false)
    private Integer sessionsCompleted;

    @Column(name = "offers_free_session", nullable = false)
    private boolean offersFreeSession;

    @Column(name = "trial_session_weekday")
    private Integer trialSessionWeekday;

    @Column(name = "trial_session_start_time")
    private LocalTime trialSessionStartTime;

    @Column(name = "trial_session_end_time")
    private LocalTime trialSessionEndTime;

    @PrePersist
    void onCreate() {
        if (hourlyRateCents == null) {
            hourlyRateCents = 0;
        }
        if (verificationStatus == null) {
            verificationStatus = VerificationStatus.PENDING;
        }
        if (adminNote == null) {
            adminNote = "";
        }
        if (sessionsCompleted == null) {
            sessionsCompleted = 0;
        }
    }
}
