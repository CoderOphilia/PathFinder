package com.pathfinder.mentor.repo;

import com.pathfinder.mentor.domain.MentorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MentorAvailabilityRepository extends JpaRepository<MentorAvailability, Long> {
    List<MentorAvailability> findByMentorProfileUserIdOrderByWeekdayAscStartTimeAsc(Long mentorUserId);

    void deleteByMentorProfileUserId(Long mentorUserId);
}
