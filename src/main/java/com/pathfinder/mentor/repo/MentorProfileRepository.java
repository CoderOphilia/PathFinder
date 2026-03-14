package com.pathfinder.mentor.repo;

import com.pathfinder.mentor.domain.MentorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MentorProfileRepository extends JpaRepository<MentorProfile, Long> {
}
