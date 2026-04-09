package com.pathfinder.session.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.service.UserService;
import com.pathfinder.mentor.domain.MentorProfile;
import com.pathfinder.mentor.repo.MentorProfileRepository;
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

    private static final int PLATFORM_TRIAL_SESSION_LIMIT = 2;
    private static final EnumSet<SessionStatus> ACTIVE_SLOT_STATUSES =
            EnumSet.of(SessionStatus.REQUESTED, SessionStatus.APPROVED, SessionStatus.PAID);
    private static final EnumSet<SessionStatus> FREE_SESSION_HISTORY_STATUSES =
            EnumSet.of(SessionStatus.REQUESTED, SessionStatus.APPROVED, SessionStatus.PAID, SessionStatus.COMPLETED);

    private final SessionRequestRepository sessionRequestRepository;
    private final UserService userService;
    private final MentorProfileRepository mentorProfileRepository;

    public SessionService(
            SessionRequestRepository sessionRequestRepository,
            UserService userService,
            MentorProfileRepository mentorProfileRepository
    ) {
        this.sessionRequestRepository = sessionRequestRepository;
        this.userService = userService;
        this.mentorProfileRepository = mentorProfileRepository;
    }

    public SessionRequest createSession(
            String menteeEmail,
            String mentorEmail,
            String mentorName,
            String slotTime,
            String sessionType,
            String objective,
            String bookingNotes,
            boolean requestFreeSession
    ) {
        User mentee = requireUser(menteeEmail, "mentee");
        User mentor = requireUser(mentorEmail, "mentor");
        MentorProfile mentorProfile = mentorProfileRepository.findById(mentor.getId()).orElse(null);
        BookingPolicy bookingPolicy = buildBookingPolicy(mentee.getEmail(), mentor.getEmail(), mentorProfile);

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
        if (requestFreeSession && !bookingPolicy.freeSessionAvailable()) {
            throw new IllegalArgumentException("This mentor no longer has a free session available for you.");
        }
        if (!requestFreeSession && bookingPolicy.freeSessionAvailable()) {
            throw new IllegalArgumentException("Request the mentor's free session before purchasing a paid one.");
        }

        SessionRequest request = new SessionRequest();
        request.setMenteeEmail(mentee.getEmail());
        request.setMenteeUserId(mentee.getId());
        request.setMentorEmail(mentor.getEmail());
        request.setMentorUserId(mentor.getId());
        request.setMentorName(normalizeText(mentorName).isEmpty() ? buildFullName(mentor) : normalizeText(mentorName));
        request.setSlotTime(normalizedSlotTime);
        request.setSessionType(requireText(sessionType, "Session type is required."));
        request.setObjective(requireText(objective, "Objective is required."));
        request.setBookingNotes(normalizeText(bookingNotes));
        request.setMeetingLink("");
        request.setStatus(SessionStatus.REQUESTED);
        request.setPaymentCompleted(false);
        request.setFreeSessionRequested(requestFreeSession);
        request.setQuotedAmountCents(requestFreeSession
                ? 0
                : mentorProfile == null || mentorProfile.getHourlyRateCents() == null ? 0 : mentorProfile.getHourlyRateCents());
        return sessionRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public BookingPolicy getBookingPolicy(String menteeEmail, String mentorEmail) {
        User mentor = requireUser(mentorEmail, "mentor");
        MentorProfile mentorProfile = mentorProfileRepository.findById(mentor.getId()).orElse(null);
        String normalizedMenteeEmail = normalizeEmail(menteeEmail);
        if (normalizedMenteeEmail.isEmpty()) {
            return buildBookingPolicy("", mentor.getEmail(), mentorProfile);
        }
        requireUser(normalizedMenteeEmail, "mentee");
        return buildBookingPolicy(normalizedMenteeEmail, mentor.getEmail(), mentorProfile);
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

    public SessionRequest approveSession(Long sessionId, String meetingLink) {
        SessionRequest request = requireSession(sessionId);
        if (request.getStatus() != SessionStatus.REQUESTED) {
            throw new IllegalArgumentException("Only requested sessions can be approved.");
        }
        String normalizedMeetingLink = normalizeText(meetingLink);
        if (normalizedMeetingLink.isEmpty()) {
            throw new IllegalArgumentException("Meeting link is required to approve a session.");
        }
        request.setStatus(SessionStatus.APPROVED);
        request.setMeetingLink(normalizedMeetingLink);
        return sessionRequestRepository.save(request);
    }

    public SessionRequest declineSession(Long sessionId, String meetingLink) {
        SessionRequest request = requireSession(sessionId);
        if (request.getStatus() != SessionStatus.REQUESTED) {
            throw new IllegalArgumentException("Only requested sessions can be declined.");
        }
        request.setStatus(SessionStatus.DECLINED);
        request.setMeetingLink(normalizeText(meetingLink));
        return sessionRequestRepository.save(request);
    }

    public SessionRequest paySession(Long sessionId) {
        SessionRequest request = requireSession(sessionId);
        if (request.isFreeSessionRequested()) {
            throw new IllegalArgumentException("Free sessions do not require payment.");
        }
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
        boolean completableFreeSession = request.isFreeSessionRequested()
                && request.getStatus() == SessionStatus.APPROVED;
        boolean completablePaidSession = !request.isFreeSessionRequested()
                && request.getStatus() == SessionStatus.PAID
                && request.isPaymentCompleted();
        if (!completableFreeSession && !completablePaidSession) {
            throw new IllegalArgumentException("Only approved free sessions or paid sessions can be completed.");
        }
        if (completablePaidSession) {
            request.setPaymentCompleted(true);
            if (request.getQuotedAmountCents() == null || request.getQuotedAmountCents() <= 0) {
                request.setQuotedAmountCents(resolveMentorRateCents(request));
            }
        }
        request.setStatus(SessionStatus.COMPLETED);
        incrementMentorCompletionCount(request);
        return sessionRequestRepository.save(request);
    }

    private SessionRequest requireSession(Long sessionId) {
        return sessionRequestRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found."));
    }

    private BookingPolicy buildBookingPolicy(String menteeEmail, String mentorEmail, MentorProfile mentorProfile) {
        boolean mentorOffersFreeSession = mentorProfile != null && mentorProfile.isOffersFreeSession();
        boolean mentorTrialSlotConfigured = mentorProfile != null
                && mentorProfile.getTrialSessionWeekday() != null
                && mentorProfile.getTrialSessionStartTime() != null
                && mentorProfile.getTrialSessionEndTime() != null;
        boolean hasUsedFreeSessionWithMentor = !normalizeEmail(menteeEmail).isEmpty()
                && sessionRequestRepository.existsByMenteeEmailAndMentorEmailAndFreeSessionRequestedTrueAndStatusIn(
                normalizeEmail(menteeEmail),
                normalizeEmail(mentorEmail),
                FREE_SESSION_HISTORY_STATUSES
        );
        long platformTrialSessionsUsed = normalizeEmail(menteeEmail).isEmpty()
                ? 0
                : sessionRequestRepository.countByMenteeEmailAndFreeSessionRequestedTrueAndStatusIn(
                        normalizeEmail(menteeEmail),
                        FREE_SESSION_HISTORY_STATUSES
                );
        int remainingPlatformTrialSessions = (int) Math.max(0, PLATFORM_TRIAL_SESSION_LIMIT - platformTrialSessionsUsed);
        boolean freeSessionAvailable = mentorOffersFreeSession
                && mentorTrialSlotConfigured
                && !hasUsedFreeSessionWithMentor
                && remainingPlatformTrialSessions > 0;
        return new BookingPolicy(
                mentorOffersFreeSession,
                hasUsedFreeSessionWithMentor,
                freeSessionAvailable,
                mentorTrialSlotConfigured,
                (int) platformTrialSessionsUsed,
                remainingPlatformTrialSessions
        );
    }

    private void incrementMentorCompletionCount(SessionRequest request) {
        Long mentorUserId = request.getMentorUserId();
        if (mentorUserId == null && !normalizeEmail(request.getMentorEmail()).isEmpty()) {
            User mentor = requireUser(request.getMentorEmail(), "mentor");
            mentorUserId = mentor.getId();
        }
        if (mentorUserId == null) {
            return;
        }
        mentorProfileRepository.findById(mentorUserId).ifPresent(profile -> {
            int completed = profile.getSessionsCompleted() == null ? 0 : profile.getSessionsCompleted();
            profile.setSessionsCompleted(completed + 1);
        });
    }

    private int resolveMentorRateCents(SessionRequest request) {
        Long mentorUserId = request.getMentorUserId();
        if (mentorUserId == null && !normalizeEmail(request.getMentorEmail()).isEmpty()) {
            User mentor = requireUser(request.getMentorEmail(), "mentor");
            mentorUserId = mentor.getId();
        }
        if (mentorUserId == null) {
            return 0;
        }
        return mentorProfileRepository.findById(mentorUserId)
                .map(MentorProfile::getHourlyRateCents)
                .orElse(0);
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

    public record BookingPolicy(
            boolean mentorOffersFreeSession,
            boolean menteeAlreadyUsedFreeSessionWithMentor,
            boolean freeSessionAvailable,
            boolean mentorTrialSlotConfigured,
            int platformTrialSessionsUsed,
            int remainingPlatformTrialSessions
    ) {
    }
}
