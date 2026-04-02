package com.pathfinder.mentee.domain;


import com.pathfinder.auth.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mentee_profiles")
@Getter
@Setter
@NoArgsConstructor
public class MenteeProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;


    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "target_role", nullable = false)
    private String targetRole;

    @Column(name = "experience_level", nullable = false)
    private MenteeExperienceLevel experienceLevel;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(columnDefinition = "TEXT")
    private String currentGoals;


}
