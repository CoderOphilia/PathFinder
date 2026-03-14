package com.pathfinder.mentor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MentorSkillId implements Serializable {

    @Column(name = "mentor_user_id")
    private Long mentorUserId;

    @Column(name = "skill_name", length = 100)
    private String skillName;
}
