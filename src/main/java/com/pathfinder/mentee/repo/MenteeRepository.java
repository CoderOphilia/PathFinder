package com.pathfinder.mentee.repo;

import com.pathfinder.mentee.domain.MenteeProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenteeRepository extends JpaRepository<MenteeProfile, Long> {
}
