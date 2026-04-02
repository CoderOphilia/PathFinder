package com.pathfinder.session.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.service.UserService;
import com.pathfinder.session.domain.SessionRequest;
import com.pathfinder.session.domain.SessionStatus;
import com.pathfinder.session.repo.SessionRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;

@Service
@Transactional
public class SessionService {

    private static final EnumSet<SessionStatus> ACTIVE_SLOT_STATUSES =
            EnumSet.of(SessionStatus.REQUESTED, SessionStatus.APPROVED, SessionStatus.PAID);

    private final SessionRequestRepository sessionRequestRepository;
    private final UserService userService;

    public SessionService(SessionRequestRepository sessionRequestRepository, UserService userService) {
        this.sessionRequestRepository = sessionRequestRepository;
        this.userService = userService;
    }

    public SessionRequest createSession(
            String menteeEmail,
            String mentorEmail,
            String mentorName,
            String slotTime,
            String sessionType,
            String objective,
            String bookingNotes
    ) {
        User mentee = requireUser(menteeEmail, "mentee");
        User mentor = requireUser(mentorEmail, "mentor");

        String normalizedSlotTime = normalizeText(slotTime);
        if (normalizedSlotTime.isEmpty()) {
            throw new IllegalArgumentException("Slot time is required.");
        }
        if (sessionRequestRepository.existsByMentorEmailAndSlotTimeAndStatusIn(
                mentor.getEmail(),
                normalizedSlotTime,
                ACTIVE_SLOT_STATUSES
        )) {
            throw new IllegalArgumentException("That time slot is already booked.");
        }

        SessionRequest request = new SessionRequest();
        request.setMenteeEmail(mentee.getEmail());
        request.setMentorEmail(mentor.getEmail());
        request.setMentorName(normalizeText(mentorName).isEmpty() ? buildFullName(mentor) : normalizeText(mentorName));
        request.setSlotTime(normalizedSlotTime);
        request.setSessionType(requireText(sessionType, "Session type is required."));
        request.setObjective(requireText(objective, "Objective is required."));
        request.setBookingNotes(normalizeText(bookingNotes));
        request.setMentorNote("");
        request.setStatus(SessionStatus.REQUESTED);
        request.setPaymentCompleted(false);
        return sessionRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public SessionRequest getSessionById(Long id) {
        return sessionRequestRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<SessionRequest> getSessionsForMentor(String mentorEmail) {
        return sessionRequestRepository.findByMentorEmailOrderByCreatedAtDesc(normalizeEmail(mentorEmail));
    }

    @Transactional(readOnly = true)
    public List<SessionRequest> getSessionsForMentee(String menteeEmail) {
        return  sessionRequestRepository.findByMenteeEmailOrderByCreatedAtDesc(menteeEmail);
    }

    public SessionRequest approveSession(Long sessionId, String mentorNote) {
        SessionRequest request = requireSession(sessionId);
        if (request.getStatus() != SessionStatus.REQUESTED) {
            throw new IllegalArgumentException("Only requested sessions can be approved.");
        }
        request.setStatus(SessionStatus.APPROVED);
        request.setMentorNote(normalizeText(mentorNote));
        return sessionRequestRepository.save(request);
    }

    public SessionRequest declineSession(Long sessionId, String mentorNote) {
        SessionRequest request = requireSession(sessionId);
        if (request.getStatus() != SessionStatus.REQUESTED) {
            throw new IllegalArgumentException("Only requested sessions can be declined.");
        }
        request.setStatus(SessionStatus.DECLINED);
        request.setMentorNote(normalizeText(mentorNote));
        return sessionRequestRepository.save(request);
    }

    public SessionRequest paySession(Long sessionId) {
        SessionRequest request = requireSession(sessionId);
        if (request.getStatus() != SessionStatus.APPROVED) {
            throw new IllegalArgumentException("Only approved sessions can be paid.");
        }
        request.setStatus(SessionStatus.PAID);
        request.setPaymentCompleted(true);
        return sessionRequestRepository.save(request);
    }

    public SessionRequest cancelSession(Long sessionId) {
        SessionRequest request = requireSession(sessionId);
        if (request.getStatus() == SessionStatus.CANCELLED
                || request.getStatus() == SessionStatus.COMPLETED
                || request.getStatus() == SessionStatus.DECLINED) {
            throw new IllegalArgumentException("This session cannot be cancelled.");
        }
        request.setStatus(SessionStatus.CANCELLED);
        return sessionRequestRepository.save(request);
    }

    public SessionRequest completeSession(Long sessionId) {
        SessionRequest request = requireSession(sessionId);
        if (request.getStatus() != SessionStatus.PAID) {
            throw new IllegalArgumentException("Only paid sessions can be completed.");
        }
        request.setStatus(SessionStatus.COMPLETED);
        return sessionRequestRepository.save(request);
    }

    private SessionRequest requireSession(Long sessionId) {
        return sessionRequestRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found."));
    }

    private User requireUser(String email, String expectedRole) {
        User user = userService.findUserByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + normalizeText(email));
        }
        if (!matchesRole(user.getRole(), expectedRole)) {
            throw new IllegalArgumentException("User does not have the required role: " + expectedRole);
        }
        return user;
    }

    private boolean matchesRole(String actualRole, String expectedRole) {
        String normalizedActual = normalizeText(actualRole).toLowerCase();
        if ("mentee".equals(expectedRole)) {
            return "mentee".equals(normalizedActual);
        }
        return expectedRole.equals(normalizedActual);
    }

    private String buildFullName(User user) {
        return (normalizeText(user.getFirstName()) + " " + normalizeText(user.getLastName())).trim();
    }

    private String requireText(String value, String errorMessage) {
        String normalized = normalizeText(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private String normalizeEmail(String email) {
        return normalizeText(email).toLowerCase();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
