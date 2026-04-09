package com.pathfinder.admin.web;

import com.pathfinder.admin.service.AdminAccountService;
import com.pathfinder.admin.service.AdminReviewService;
import com.pathfinder.admin.service.AdminSessionOversightService;
import com.pathfinder.auth.config.SecurityConfig;
import com.pathfinder.auth.config.SessionRoleAuthenticationFilter;
import com.pathfinder.auth.web.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, SessionRoleAuthenticationFilter.class})
class AdminControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminReviewService adminReviewService;

    @MockBean
    private AdminAccountService adminAccountService;

    @MockBean
    private AdminSessionOversightService adminSessionOversightService;

    @Test
    // Renders the admin home page with top-level counts.
    void home() throws Exception {
        when(adminReviewService.pendingReviewCount()).thenReturn(2L);
        when(adminAccountService.totalUserCount()).thenReturn(10L);
        when(adminSessionOversightService.activeSessionCount()).thenReturn(3L);

        mockMvc.perform(get("/admin/home")
                        .sessionAttr(AuthController.SESSION_USER_EMAIL, "admin@example.com")
                        .sessionAttr(AuthController.SESSION_USER_ROLE, "admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "admin/home :: content"))
                .andExpect(model().attribute("pendingMentorReviewCount", 2L))
                .andExpect(model().attribute("managedUserCount", 10L))
                .andExpect(model().attribute("activeSessionCount", 3L));
    }

    @Test
    // Renders mentor review rows on the queue page.
    void mentorReview() throws Exception {
        AdminReviewService.MentorReviewSummaryView item = new AdminReviewService.MentorReviewSummaryView(
                "mentor-user",
                "Mentor User",
                "mentor@example.com",
                "Staff Engineer @ Example",
                "Pending review",
                "statusBadge statusBadge--requested",
                "7/7 complete",
                "7/7"
        );
        when(adminReviewService.listReviewItems()).thenReturn(List.of(item));

        mockMvc.perform(get("/admin/mentors/review")
                        .sessionAttr(AuthController.SESSION_USER_EMAIL, "admin@example.com")
                        .sessionAttr(AuthController.SESSION_USER_ROLE, "admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "admin/mentor_review :: content"))
                .andExpect(model().attribute("reviewItems", List.of(item)));
    }

    @Test
    // Renders the dedicated mentor review detail page.
    void mentorReviewDetail() throws Exception {
        AdminReviewService.MentorReviewDetailView item = new AdminReviewService.MentorReviewDetailView(
                "mentor-user",
                "Mentor User",
                "mentor@example.com",
                "",
                "Staff Engineer @ Example",
                "Backend interviews",
                "$80.00",
                "Experienced mentor",
                List.of("System design"),
                List.of("Meta"),
                "Pending review",
                "statusBadge statusBadge--requested",
                "",
                "Complete (7/7)",
                List.of(new AdminReviewService.VerificationCheckView("Bio", true, "Added")),
                3,
                true,
                "Monday • 6:00 PM - 6:30 PM"
        );
        when(adminReviewService.findReviewItem("mentor-user")).thenReturn(item);

        mockMvc.perform(get("/admin/mentors/review/mentor-user")
                        .sessionAttr(AuthController.SESSION_USER_EMAIL, "admin@example.com")
                        .sessionAttr(AuthController.SESSION_USER_ROLE, "admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "admin/mentor_review_detail :: content"))
                .andExpect(model().attribute("selectedReviewItem", item));
    }

    @Test
    // Renders user moderation rows.
    void users() throws Exception {
        when(adminAccountService.listUsers()).thenReturn(List.of(
                new AdminAccountService.ManagedUserView(1L, "Test User", "user@example.com", "Mentee", "ACTIVE", true, false)
        ));

        mockMvc.perform(get("/admin/users")
                        .sessionAttr(AuthController.SESSION_USER_EMAIL, "admin@example.com")
                        .sessionAttr(AuthController.SESSION_USER_ROLE, "admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "admin/users :: content"))
                .andExpect(model().attributeExists("managedUsers"));
    }

    @Test
    // Renders session oversight rows.
    void sessions() throws Exception {
        when(adminSessionOversightService.listRequests()).thenReturn(List.of(
                new AdminSessionOversightService.SessionOversightItemView(
                        1L,
                        "Mentor User",
                        "mentee@example.com",
                        "Monday • 6:00 PM - 6:45 PM (America/Vancouver)",
                        "Mock interview",
                        "Requested",
                        "statusBadge statusBadge--requested",
                        "Not paid",
                        "statusBadge statusBadge--neutral",
                        "Mon, Apr 7 • 11:00 AM",
                        true
                )
        ));

        mockMvc.perform(get("/admin/sessions")
                        .sessionAttr(AuthController.SESSION_USER_EMAIL, "admin@example.com")
                        .sessionAttr(AuthController.SESSION_USER_ROLE, "admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "admin/sessions :: content"))
                .andExpect(model().attributeExists("sessionItems"));
    }

    @Test
    // Posts an approve action and redirects back to the detail page.
    void approveMentor() throws Exception {
        mockMvc.perform(post("/admin/mentors/review/mentor-user/approve")
                        .param("adminNote", "Looks good.")
                        .sessionAttr(AuthController.SESSION_USER_EMAIL, "admin@example.com")
                        .sessionAttr(AuthController.SESSION_USER_ROLE, "admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/mentors/review/mentor-user"))
                .andExpect(flash().attributeExists("flashMessage"));

        verify(adminReviewService).approveMentor("mentor-user", "Looks good.");
    }

    @Test
    // Keeps the admin on the detail page when denial validation fails.
    void denyMentorValidationError() throws Exception {
        doThrow(new IllegalArgumentException("Enter a denial note before rejecting the mentor."))
                .when(adminReviewService).denyMentor("mentor-user", "");

        mockMvc.perform(post("/admin/mentors/review/mentor-user/deny")
                        .param("adminNote", "")
                        .sessionAttr(AuthController.SESSION_USER_EMAIL, "admin@example.com")
                        .sessionAttr(AuthController.SESSION_USER_ROLE, "admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/mentors/review/mentor-user"))
                .andExpect(flash().attribute("formError", "Enter a denial note before rejecting the mentor."));

        verify(adminReviewService).denyMentor("mentor-user", "");
    }

}
