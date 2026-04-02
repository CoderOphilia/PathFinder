package com.pathfinder.session.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.service.UserService;
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

    @InjectMocks
    private SessionService sessionService;

    @Test
    // Creates a new session request.
    void createSession() {
        User mentee = createUser("mentee@example.com", "mentee", "Mentee", "User");
        User mentor = createUser("mentor@example.com", "mentor", "Mentor", "User");

        when(userService.findUserByEmail("mentee@example.com")).thenReturn(mentee);
        when(userService.findUserByEmail("mentor@example.com")).thenReturn(mentor);
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
                "Focus on confidence"
        );

        assertEquals("mentee@example.com", result.getMenteeEmail());
        assertEquals("mentor@example.com", result.getMentorEmail());
        assertEquals(SessionStatus.REQUESTED, result.getStatus());
        assertFalse(result.isPaymentCompleted());
        verify(sessionRequestRepository).save(any(SessionRequest.class));
    }

    @Test
    // Rejects booking the same mentor slot twice.
    void rejectDuplicateSlot() {
        User mentee = createUser("mentee@example.com", "mentee", "Mentee", "User");
        User mentor = createUser("mentor@example.com", "mentor", "Mentor", "User");

        when(userService.findUserByEmail("mentee@example.com")).thenReturn(mentee);
        when(userService.findUserByEmail("mentor@example.com")).thenReturn(mentor);
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
                        ""
                )
        );

        assertTrue(exception.getMessage().contains("already booked"));
    }

    @Test
    // Approves a requested session.
    void approveSession() {
        SessionRequest request = new SessionRequest();
        request.setId(1L);
        request.setStatus(SessionStatus.REQUESTED);

        when(sessionRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(sessionRequestRepository.save(any(SessionRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SessionRequest result = sessionService.approveSession(1L, "See you soon");

        assertEquals(SessionStatus.APPROVED, result.getStatus());
        assertEquals("See you soon", result.getMentorNote());
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
    // Blocks completion before payment.
    void blockCompleteBeforePayment() {
        SessionRequest request = new SessionRequest();
        request.setId(1L);
        request.setStatus(SessionStatus.APPROVED);

        when(sessionRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                sessionService.completeSession(1L)
        );

        assertTrue(exception.getMessage().contains("Only paid sessions"));
    }

    private User createUser(String email, String role, String firstName, String lastName) {
        User user = new User();
        user.setEmail(email);
        user.setRole(role);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        return user;
    }
}
