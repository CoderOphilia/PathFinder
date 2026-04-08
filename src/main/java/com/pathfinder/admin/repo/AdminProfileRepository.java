package com.pathfinder.admin.repo;

import com.pathfinder.admin.domain.AdminProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminProfileRepository extends JpaRepository<AdminProfile, Long> {
}
