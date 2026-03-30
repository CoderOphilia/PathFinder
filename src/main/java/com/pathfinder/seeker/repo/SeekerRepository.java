package com.pathfinder.seeker.repo;

import com.pathfinder.auth.domain.User;
import com.pathfinder.seeker.domain.SeekerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeekerRepository extends JpaRepository<SeekerProfile, Long> {

}
