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
@Table(name = "mentor_interview_companies")
@Getter
@Setter
@NoArgsConstructor
public class MentorInterviewCompany {

    @EmbeddedId
    private MentorInterviewCompanyId id = new MentorInterviewCompanyId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("mentorUserId")
    @JoinColumn(name = "mentor_user_id", nullable = false)
    private MentorProfile mentorProfile;

    @Column(name = "company_name", nullable = false, length = 120, insertable = false, updatable = false)
    private String companyName;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = new MentorInterviewCompanyId();
        }
        if (mentorProfile != null) {
            id.setMentorUserId(mentorProfile.getUserId());
        }
        id.setCompanyName(companyName);
    }
}
