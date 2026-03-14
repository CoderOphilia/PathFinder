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
public class MentorInterviewCompanyId implements Serializable {

    @Column(name = "mentor_user_id")
    private Long mentorUserId;

    @Column(name = "company_name", length = 120)
    private String companyName;
}
