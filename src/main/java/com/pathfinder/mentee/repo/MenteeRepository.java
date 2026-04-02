package com.pathfinder.mentee.repo;

import com.pathfinder.auth.domain.User;
import com.pathfinder.mentee.domain.MenteeProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MenteeRepository extends JpaRepository<MenteeProfile, Long> {
    Optional<MenteeProfile> findByUser(User user);
}
