package com.pathfinder.session.web;

import com.pathfinder.auth.web.AuthController;
import com.pathfinder.mentee.service.MenteeProfileService;
import com.pathfinder.mentee.web.MenteeController;
import com.pathfinder.mentor.service.MentorProfileService;
import com.pathfinder.session.domain.SessionRequest;
import com.pathfinder.session.domain.SessionStatus;
import com.pathfinder.session.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({SessionController.class, MenteeController.class})
class SessionCompletionWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MentorProfileService mentorProfileService;

    @MockBean
    private MenteeProfileService menteeProfileService;

    @MockBean
    private SessionService sessionService;

    @Test
    // Mentors can finish a free approved session from their dashboard.
    void mentorCanMarkApprovedFreeSessionDone() throws Exception {
        SessionRequest completedRequest = sessionRequest(1L, SessionStatus.COMPLETED);
        completedRequest.setFreeSessionRequested(true);
        when(sessionService.completeSession(1L)).thenReturn(completedRequest);

        mockMvc.perform(post("/mentor/sessions/1/complete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentor/requests"))
                .andExpect(flash().attributeExists("flashMessage"));

        verify(sessionService).completeSession(1L);
    }

    @Test
    // Completed sessions should render the completed badge on the detail page.
    void menteeSessionDetailReflectsCompletedStatus() throws Exception {
        when(sessionService.getSessionById(1L)).thenReturn(sessionRequest(1L, SessionStatus.COMPLETED));

        mockMvc.perform(get("/mentee/sessions/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "mentee/session_detail :: content"))
                .andExpect(model().attribute("statusLabel", "Completed"))
                .andExpect(model().attribute("statusClass", "statusBadge statusBadge--completed"));
    }

    @Test
    // Approved paid sessions should send the mentee straight to payment.
    void approvedPaidSessionRedirectsToPayment() throws Exception {
        SessionRequest approvedPaidRequest = sessionRequest(1L, SessionStatus.APPROVED);
        approvedPaidRequest.setFreeSessionRequested(false);
        approvedPaidRequest.setPaymentCompleted(false);

        when(sessionService.getSessionById(1L)).thenReturn(approvedPaidRequest);

        mockMvc.perform(get("/mentee/sessions/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentee/sessions/1/payment"))
                .andExpect(flash().attributeExists("flashMessage"));
    }

    @Test
    // The mentee home page now shows the last completed session as recent activity.
    void menteeHomeShowsCompletedSessionAsRecentActivity() throws Exception {
        String menteeEmail = "mentee@example.com";
        SessionRequest approvedRequest = sessionRequest(1L, SessionStatus.APPROVED);
        SessionRequest completedRequest = sessionRequest(2L, SessionStatus.COMPLETED);

        when(menteeProfileService.getNextSession(menteeEmail)).thenReturn(Optional.of(approvedRequest));
        when(menteeProfileService.getLatestCompletedSession(menteeEmail)).thenReturn(Optional.of(completedRequest));
        when(menteeProfileService.getMenteeSession(menteeEmail)).thenReturn(List.of(completedRequest, approvedRequest));
        when(menteeProfileService.getPendingCount(menteeEmail)).thenReturn(0L);
        when(menteeProfileService.buildCalenderDays(List.of(completedRequest, approvedRequest))).thenReturn(List.of());

        mockMvc.perform(get("/mentee/home")
                        .sessionAttr(AuthController.SESSION_USER_EMAIL, menteeEmail)
                        .sessionAttr(AuthController.SESSION_USER_ROLE, "mentee"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "mentee/home :: content"))
                .andExpect(model().attribute("nextSession", approvedRequest))
                .andExpect(model().attribute("latestCompletedSession", completedRequest));
    }

    private SessionRequest sessionRequest(Long id, SessionStatus status) {
        SessionRequest request = new SessionRequest();
        request.setId(id);
        request.setMenteeEmail("mentee@example.com");
        request.setMentorEmail("mentor@example.com");
        request.setMentorName("Mentor User");
        request.setSlotTime("Monday • 6:00 PM - 6:45 PM (America/Vancouver)");
        request.setSessionType("Mock interview");
        request.setObjective("Practice behavioral answers");
        request.setBookingNotes("");
        request.setMentorNote("");
        request.setStatus(status);
        request.setPaymentCompleted(status == SessionStatus.PAID || status == SessionStatus.COMPLETED);
        request.setFreeSessionRequested(false);
        request.setCreatedAt(LocalDateTime.now());
        return request;
    }
}
