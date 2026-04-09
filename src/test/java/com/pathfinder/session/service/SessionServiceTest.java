package com.pathfinder.session.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.service.UserService;
import com.pathfinder.mentor.domain.MentorProfile;
import com.pathfinder.mentor.repo.MentorProfileRepository;
import com.pathfinder.session.domain.SessionRequest;
import com.pathfinder.session.domain.SessionStatus;
import com.pathfinder.session.repo.SessionRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRequestRepository sessionRequestRepository;

    @Mock
    private UserService userService;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @InjectMocks
    private SessionService sessionService;

    @Test
    // Creates a new session request.
    void createSession() {
        User mentee = createUser("mentee@example.com", "mentee", "Mentee", "User");
        User mentor = createUser("mentor@example.com", "mentor", "Mentor", "User");
        MentorProfile mentorProfile = createMentorProfile(mentor.getId(), false, 8000);

        when(userService.findUserByEmail("mentee@example.com")).thenReturn(mentee);
        when(userService.findUserByEmail("mentor@example.com")).thenReturn(mentor);
        when(mentorProfileRepository.findById(mentor.getId())).thenReturn(Optional.of(mentorProfile));
        when(sessionRequestRepository.existsByMentorEmailAndSlotTimeAndStatusIn(
                eq("mentor@example.com"),
                eq("Mon 6:00 PM"),
                any(EnumSet.class)
        )).thenReturn(false);
        when(sessionRequestRepository.save(any(SessionRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SessionRequest result = sessionService.createSession(
                "mentee@example.com",
                "mentor@example.com",
                "Mentor User",
                "Mon 6:00 PM",
                "Mock interview",
                "Practice behavioral questions",
                "Focus on confidence",
                false
        );

        assertEquals("mentee@example.com", result.getMenteeEmail());
        assertEquals("mentor@example.com", result.getMentorEmail());
        assertEquals(SessionStatus.REQUESTED, result.getStatus());
        assertFalse(result.isPaymentCompleted());
        assertFalse(result.isFreeSessionRequested());
        assertEquals(8000, result.getQuotedAmountCents());
        verify(sessionRequestRepository).save(any(SessionRequest.class));
    }

    @Test
    // Rejects booking the same mentor slot twice.
    void rejectDuplicateSlot() {
        User mentee = createUser("mentee@example.com", "mentee", "Mentee", "User");
        User mentor = createUser("mentor@example.com", "mentor", "Mentor", "User");
        MentorProfile mentorProfile = createMentorProfile(mentor.getId(), false, 8000);

        when(userService.findUserByEmail("mentee@example.com")).thenReturn(mentee);
        when(userService.findUserByEmail("mentor@example.com")).thenReturn(mentor);
        when(mentorProfileRepository.findById(mentor.getId())).thenReturn(Optional.of(mentorProfile));
        when(sessionRequestRepository.existsByMentorEmailAndSlotTimeAndStatusIn(
                eq("mentor@example.com"),
                eq("Mon 6:00 PM"),
                any(EnumSet.class)
        )).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                sessionService.createSession(
                        "mentee@example.com",
                        "mentor@example.com",
                        "Mentor User",
                        "Mon 6:00 PM",
                        "Mock interview",
                        "Practice behavioral questions",
                        "",
                        false
                )
        );

        assertTrue(exception.getMessage().contains("already booked"));
    }

    @Test
    // Allows a mentor's one-time free session when the mentee has not used it yet.
    void createFreeSession() {
        User mentee = createUser("mentee@example.com", "mentee", "Mentee", "User");
        User mentor = createUser("mentor@example.com", "mentor", "Mentor", "User");
        MentorProfile mentorProfile = createMentorProfile(mentor.getId(), true, 9000);

        when(userService.findUserByEmail("mentee@example.com")).thenReturn(mentee);
        when(userService.findUserByEmail("mentor@example.com")).thenReturn(mentor);
        when(mentorProfileRepository.findById(mentor.getId())).thenReturn(Optional.of(mentorProfile));
        when(sessionRequestRepository.existsByMentorEmailAndSlotTimeAndStatusIn(
                eq("mentor@example.com"),
                eq("Tue 7:00 PM"),
                any(EnumSet.class)
        )).thenReturn(false);
        when(sessionRequestRepository.existsByMenteeEmailAndMentorEmailAndFreeSessionRequestedTrueAndStatusIn(
                eq("mentee@example.com"),
                eq("mentor@example.com"),
                any(EnumSet.class)
        )).thenReturn(false);
        when(sessionRequestRepository.save(any(SessionRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SessionRequest result = sessionService.createSession(
                "mentee@example.com",
                "mentor@example.com",
                "Mentor User",
                "Tue 7:00 PM",
                "Career strategy",
                "Talk through next steps",
                "",
                true
        );

        assertTrue(result.isFreeSessionRequested());
        assertEquals(0, result.getQuotedAmountCents());
    }

    @Test
    // Prevents a mentee from skipping an available free session and requesting paid immediately.
    void rejectPaidSessionWhenFreeIsStillAvailable() {
        User mentee = createUser("mentee@example.com", "mentee", "Mentee", "User");
        User mentor = createUser("mentor@example.com", "mentor", "Mentor", "User");
        MentorProfile mentorProfile = createMentorProfile(mentor.getId(), true, 9000);

        when(userService.findUserByEmail("mentee@example.com")).thenReturn(mentee);
        when(userService.findUserByEmail("mentor@example.com")).thenReturn(mentor);
        when(mentorProfileRepository.findById(mentor.getId())).thenReturn(Optional.of(mentorProfile));
        when(sessionRequestRepository.existsByMentorEmailAndSlotTimeAndStatusIn(
                eq("mentor@example.com"),
                eq("Wed 5:00 PM"),
                any(EnumSet.class)
        )).thenReturn(false);
        when(sessionRequestRepository.existsByMenteeEmailAndMentorEmailAndFreeSessionRequestedTrueAndStatusIn(
                eq("mentee@example.com"),
                eq("mentor@example.com"),
                any(EnumSet.class)
        )).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                sessionService.createSession(
                        "mentee@example.com",
                        "mentor@example.com",
                        "Mentor User",
                        "Wed 5:00 PM",
                        "Resume review",
                        "Resume feedback",
                        "",
                        false
                )
        );

        assertTrue(exception.getMessage().contains("free session"));
    }

    @Test
    // Approves a requested session.
    void approveSession() {
        SessionRequest request = new SessionRequest();
        request.setId(1L);
        request.setStatus(SessionStatus.REQUESTED);

        when(sessionRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(sessionRequestRepository.save(any(SessionRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SessionRequest result = sessionService.approveSession(1L, "https://zoom.us/j/123");

        assertEquals(SessionStatus.APPROVED, result.getStatus());
        assertEquals("https://zoom.us/j/123", result.getMeetingLink());
    }

    @Test
    // Marks an approved session as paid.
    void paySession() {
        SessionRequest request = new SessionRequest();
        request.setId(1L);
        request.setStatus(SessionStatus.APPROVED);
        request.setPaymentCompleted(false);

        when(sessionRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(sessionRequestRepository.save(any(SessionRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SessionRequest result = sessionService.paySession(1L);

        assertEquals(SessionStatus.PAID, result.getStatus());
        assertTrue(result.isPaymentCompleted());
    }

    @Test
    // Allows a mentor to complete a free approved trial session.
    void completeApprovedFreeSession() {
        SessionRequest request = new SessionRequest();
        request.setId(1L);
        request.setStatus(SessionStatus.APPROVED);
        request.setPaymentCompleted(false);
        request.setFreeSessionRequested(true);

        when(sessionRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(sessionRequestRepository.save(any(SessionRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SessionRequest result = sessionService.completeSession(1L);

        assertEquals(SessionStatus.COMPLETED, result.getStatus());
        assertFalse(result.isPaymentCompleted());
    }

    @Test
    // Blocks unpaid paid sessions from being marked complete before checkout.
    void rejectCompletingUnpaidApprovedPaidSession() {
        SessionRequest request = new SessionRequest();
        request.setId(1L);
        request.setStatus(SessionStatus.APPROVED);
        request.setPaymentCompleted(false);
        request.setFreeSessionRequested(false);

        when(sessionRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                sessionService.completeSession(1L)
        );

        assertTrue(exception.getMessage().contains("paid sessions"));
    }

    @Test
    // Marks a paid session as complete and preserves payment state.
    void completePaidSession() {
        SessionRequest request = new SessionRequest();
        request.setId(1L);
        request.setStatus(SessionStatus.PAID);
        request.setPaymentCompleted(true);
        request.setMentorUserId(1L);
        request.setQuotedAmountCents(0);
        MentorProfile mentorProfile = createMentorProfile(1L, false, 9000);
        mentorProfile.setSessionsCompleted(2);

        when(sessionRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(mentorProfileRepository.findById(1L)).thenReturn(Optional.of(mentorProfile));
        when(sessionRequestRepository.save(any(SessionRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SessionRequest result = sessionService.completeSession(1L);

        assertEquals(SessionStatus.COMPLETED, result.getStatus());
        assertTrue(result.isPaymentCompleted());
        assertEquals(9000, result.getQuotedAmountCents());
        assertEquals(3, mentorProfile.getSessionsCompleted());
    }

    @Test
    // Blocks completion from non-active session states.
    void blockCompletionForInvalidStatuses() {
        for (SessionStatus status : EnumSet.of(
                SessionStatus.REQUESTED,
                SessionStatus.DECLINED,
                SessionStatus.CANCELLED,
                SessionStatus.COMPLETED
        )) {
            long sessionId = status.ordinal() + 1L;
            SessionRequest request = new SessionRequest();
            request.setId(sessionId);
            request.setStatus(status);

            when(sessionRequestRepository.findById(sessionId)).thenReturn(Optional.of(request));

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    sessionService.completeSession(sessionId)
            );

            assertTrue(exception.getMessage().contains("completed"));
        }
    }

    private User createUser(String email, String role, String firstName, String lastName) {
        User user = new User();
        user.setId((long) Math.abs(email.hashCode()));
        user.setEmail(email);
        user.setRole(role);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        return user;
    }

    private MentorProfile createMentorProfile(Long userId, boolean offersFreeSession, int hourlyRateCents) {
        MentorProfile mentorProfile = new MentorProfile();
        mentorProfile.setUserId(userId);
        mentorProfile.setOffersFreeSession(offersFreeSession);
        mentorProfile.setHourlyRateCents(hourlyRateCents);
        if (offersFreeSession) {
            mentorProfile.setTrialSessionWeekday(3);
            mentorProfile.setTrialSessionStartTime(java.time.LocalTime.of(18, 0));
            mentorProfile.setTrialSessionEndTime(java.time.LocalTime.of(19, 0));
        }
        return mentorProfile;
    }
}
