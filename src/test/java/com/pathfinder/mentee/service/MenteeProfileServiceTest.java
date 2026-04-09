package com.pathfinder.mentee.service;

import com.pathfinder.auth.repo.UserRepository;
import com.pathfinder.auth.service.UserService;
import com.pathfinder.mentee.repo.MenteeRepository;
import com.pathfinder.mentor.service.MentorProfileService;
import com.pathfinder.session.domain.SessionRequest;
import com.pathfinder.session.domain.SessionStatus;
import com.pathfinder.session.repo.SessionRequestRepository;
import com.pathfinder.session.service.SessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenteeProfileServiceTest {

    @Mock
    private MenteeRepository menteeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private MentorProfileService mentorProfileService;

    @Mock
    private SessionRequestRepository sessionRequestRepository;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private MenteeProfileService menteeProfileService;

    @Test
    // Makes sure the dashboard skips completed sessions when picking the next one.
    void nextSessionExcludesCompletedSessions() {
        String menteeEmail = "mentee@example.com";
        SessionRequest completedSession = sessionRequest(1L, SessionStatus.COMPLETED, LocalDateTime.now());
        SessionRequest approvedSession = sessionRequest(2L, SessionStatus.APPROVED, LocalDateTime.now());

        when(sessionService.getSessionsForMentee(menteeEmail)).thenReturn(List.of(completedSession, approvedSession));

        Optional<SessionRequest> result = menteeProfileService.getNextSession(menteeEmail);

        assertEquals(Optional.of(approvedSession), result);
    }

    @Test
    // Makes sure the dashboard shows the newest finished session.
    void latestCompletedSessionReturnsNewestCompletedSession() {
        String menteeEmail = "mentee@example.com";
        SessionRequest olderCompletedSession = sessionRequest(1L, SessionStatus.COMPLETED, LocalDateTime.now().minusDays(2));
        SessionRequest newerCompletedSession = sessionRequest(2L, SessionStatus.COMPLETED, LocalDateTime.now().minusDays(1));
        SessionRequest approvedSession = sessionRequest(3L, SessionStatus.APPROVED, LocalDateTime.now());

        when(sessionService.getSessionsForMentee(menteeEmail)).thenReturn(List.of(
                olderCompletedSession,
                newerCompletedSession,
                approvedSession
        ));

        Optional<SessionRequest> result = menteeProfileService.getLatestCompletedSession(menteeEmail);

        assertEquals(Optional.of(newerCompletedSession), result);
    }

    private SessionRequest sessionRequest(Long id, SessionStatus status, LocalDateTime createdAt) {
        SessionRequest request = new SessionRequest();
        request.setId(id);
        request.setMenteeEmail("mentee@example.com");
        request.setMentorEmail("mentor@example.com");
        request.setMentorName("Mentor User");
        request.setSlotTime("Unscheduled");
        request.setSessionType("Mock interview");
        request.setObjective("Practice behavioral answers");
        request.setBookingNotes("");
        request.setMeetingLink("");
        request.setStatus(status);
        request.setPaymentCompleted(status == SessionStatus.PAID || status == SessionStatus.COMPLETED);
        request.setCreatedAt(createdAt);
        return request;
    }
}
