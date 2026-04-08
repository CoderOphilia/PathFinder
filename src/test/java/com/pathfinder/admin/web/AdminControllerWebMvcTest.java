package com.pathfinder.admin.web;

import com.pathfinder.admin.service.AdminAccountService;
import com.pathfinder.admin.service.AdminReviewService;
import com.pathfinder.admin.service.AdminSessionOversightService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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

        mockMvc.perform(get("/admin/home"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "admin/home :: content"))
                .andExpect(model().attribute("pendingMentorReviewCount", 2L))
                .andExpect(model().attribute("managedUserCount", 10L))
                .andExpect(model().attribute("activeSessionCount", 3L));
    }

    @Test
    // Renders mentor review rows and the selected item.
    void mentorReview() throws Exception {
        AdminReviewService.MentorReviewItemView item = new AdminReviewService.MentorReviewItemView(
                "mentor-user",
                "Mentor User",
                "Staff Engineer @ Example",
                "Experienced mentor",
                List.of("System design"),
                List.of("Meta"),
                "Pending review",
                "statusBadge statusBadge--requested",
                ""
        );
        when(adminReviewService.listReviewItems()).thenReturn(List.of(item));
        when(adminReviewService.findReviewItem("mentor-user")).thenReturn(item);

        mockMvc.perform(get("/admin/mentors/review").param("mentor", "mentor-user"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "admin/mentor_review :: content"))
                .andExpect(model().attributeExists("reviewItems"))
                .andExpect(model().attribute("selectedReviewItem", item));
    }

    @Test
    // Renders user moderation rows.
    void users() throws Exception {
        when(adminAccountService.listUsers()).thenReturn(List.of(
                new AdminAccountService.ManagedUserView(1L, "Test User", "user@example.com", "Mentee", "ACTIVE", true, false)
        ));

        mockMvc.perform(get("/admin/users"))
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

        mockMvc.perform(get("/admin/sessions"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "admin/sessions :: content"))
                .andExpect(model().attributeExists("sessionItems"));
    }

    @Test
    // Posts an approve action and redirects back to the selected mentor.
    void approveMentor() throws Exception {
        mockMvc.perform(post("/admin/mentors/review/mentor-user/approve").param("adminNote", "Looks good."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/mentors/review?mentor=mentor-user"))
                .andExpect(flash().attributeExists("flashMessage"));

        verify(adminReviewService).approveMentor("mentor-user", "Looks good.");
    }
}
