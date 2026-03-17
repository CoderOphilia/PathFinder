package com.pathfinder.mentor.repo;

import com.pathfinder.mentor.domain.MentorSkill;
import com.pathfinder.mentor.domain.MentorSkillId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MentorSkillRepository extends JpaRepository<MentorSkill, MentorSkillId> {
    List<MentorSkill> findByMentorProfileUserIdOrderBySkillNameAsc(Long mentorUserId);

    void deleteByMentorProfileUserId(Long mentorUserId);
}
