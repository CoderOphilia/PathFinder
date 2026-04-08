package com.pathfinder.admin.service;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSessionOversightServiceTest {

    @Mock
    private SessionRequestRepository sessionRequestRepository;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private AdminSessionOversightService adminSessionOversightService;

    @Test
    // Maps saved session requests into admin oversight rows.
    void listRequests() {
        when(sessionRequestRepository.findAll()).thenReturn(List.of(
                sessionRequest(1L, SessionStatus.REQUESTED, false),
                sessionRequest(2L, SessionStatus.PAID, true)
        ));

        List<AdminSessionOversightService.SessionOversightItemView> result = adminSessionOversightService.listRequests();

        assertEquals(2, result.size());
        assertEquals("Requested", result.get(1).statusLabel());
        assertEquals("Paid", result.getFirst().paymentStatusLabel());
    }

    @Test
    // Delegates session cancellation to the session service.
    void cancelRequest() {
        adminSessionOversightService.cancelRequest(1L);

        verify(sessionService).cancelSession(1L);
    }

    @Test
    // Counts only rows that can still be cancelled.
    void activeSessionCount() {
        when(sessionRequestRepository.findAll()).thenReturn(List.of(
                sessionRequest(1L, SessionStatus.REQUESTED, false),
                sessionRequest(2L, SessionStatus.CANCELLED, false),
                sessionRequest(3L, SessionStatus.APPROVED, false)
        ));

        long result = adminSessionOversightService.activeSessionCount();

        assertEquals(2L, result);
    }

    private SessionRequest sessionRequest(Long id, SessionStatus status, boolean paid) {
        SessionRequest request = new SessionRequest();
        request.setId(id);
        request.setMentorName("Mentor User");
        request.setMenteeEmail("mentee@example.com");
        request.setSlotTime("Monday • 6:00 PM - 6:45 PM (America/Vancouver)");
        request.setSessionType("Mock interview");
        request.setStatus(status);
        request.setPaymentCompleted(paid);
        request.setCreatedAt(LocalDateTime.of(2026, 4, 7, (int) (10 + id), 0));
        return request;
    }
}
