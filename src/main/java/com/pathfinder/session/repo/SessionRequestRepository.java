package com.pathfinder.session.repo;

import com.pathfinder.session.domain.SessionRequest;
import com.pathfinder.session.domain.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SessionRequestRepository extends JpaRepository<SessionRequest, Long> {

    List<SessionRequest> findByMentorEmailOrderByCreatedAtDesc(String mentorEmail);

    List<SessionRequest> findBySeekerEmailOrderByCreatedAtDesc(String seekerEmail);

    boolean existsByMentorEmailAndSlotTimeAndStatusIn(
            String mentorEmail,
            String slotTime,
            Collection<SessionStatus> statuses
    );
}
