package com.pathfinder.mentor.repo;

import com.pathfinder.mentor.domain.MentorInterviewCompany;
import com.pathfinder.mentor.domain.MentorInterviewCompanyId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MentorInterviewCompanyRepository extends JpaRepository<MentorInterviewCompany, MentorInterviewCompanyId> {
    List<MentorInterviewCompany> findByMentorProfileUserIdOrderByCompanyNameAsc(Long mentorUserId);

    void deleteByMentorProfileUserId(Long mentorUserId);
}
