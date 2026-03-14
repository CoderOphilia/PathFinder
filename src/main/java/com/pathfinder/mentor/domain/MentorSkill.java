package com.pathfinder.mentor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mentor_skills")
@Getter
@Setter
@NoArgsConstructor
public class MentorSkill {

    @EmbeddedId
    private MentorSkillId id = new MentorSkillId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("mentorUserId")
    @JoinColumn(name = "mentor_user_id", nullable = false)
    private MentorProfile mentorProfile;

    @Column(name = "skill_name", nullable = false, length = 100, insertable = false, updatable = false)
    private String skillName;

    public MentorSkill(MentorProfile mentorProfile, String skillName) {
        this.mentorProfile = mentorProfile;
        this.skillName = skillName;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = new MentorSkillId();
        }
        if (mentorProfile != null) {
            id.setMentorUserId(mentorProfile.getUserId());
        }
        id.setSkillName(skillName);
    }
}
