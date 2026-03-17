package com.pathfinder.mentor.domain;

import com.pathfinder.auth.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified;

    @Column(name = "sessions_completed", nullable = false)
    private Integer sessionsCompleted;

    @PrePersist
    void onCreate() {
        if (hourlyRateCents == null) {
            hourlyRateCents = 0;
        }
        if (isVerified == null) {
            isVerified = false;
        }
        if (sessionsCompleted == null) {
            sessionsCompleted = 0;
        }
    }
}
