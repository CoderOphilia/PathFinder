package com.pathfinder.admin.web;

import com.pathfinder.admin.service.AdminAccountService;
import com.pathfinder.admin.service.AdminReviewService;
import com.pathfinder.admin.service.AdminSessionOversightService;
import com.pathfinder.mentor.web.DemoMentorCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
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
    void usersPageRendersManagedUsersTableModel() throws Exception {
        when(adminAccountService.listUsers()).thenReturn(List.of(
                new AdminAccountService.ManagedUserView(1L, "Mentee User", "mentee@example.com", "Mentee", "ACTIVE", true, false, false)
        ));

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "admin/users :: content"))
                .andExpect(model().attributeExists("managedUsers"));
    }

    @Test
    void sessionsPageRendersOversightRows() throws Exception {
        when(adminSessionOversightService.listRequests()).thenReturn(List.of(
                new AdminSessionOversightService.SessionOversightItemView(
                        "REQ-1001",
                        "Priya K.",
                        "Thursday • 6:00 PM - 6:45 PM (America/Vancouver)",
                        "Mock interview",
                        "Requested",
                        "statusBadge statusBadge--requested",
                        "Not started",
                        "statusBadge statusBadge--neutral",
                        "Tue, Mar 31 • 9:00 AM",
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
    void mentorReviewPageRendersQueueData() throws Exception {
        DemoMentorCatalog.MentorCatalogItem mentor = new DemoMentorCatalog.MentorCatalogItem(
                "alex-m",
                "Alex M.",
                "Staff Software Engineer",
                "Meta",
                "$95/hr",
                "System design mentor.",
                "Technology",
                List.of("System design"),
                57,
                List.of("Meta")
        );
        when(adminReviewService.listReviewItems()).thenReturn(List.of(
                new AdminReviewService.MentorReviewItemView(
                        mentor,
                        "Pending review",
                        "Pending review",
                        "statusBadge statusBadge--requested",
                        ""
                )
        ));

        mockMvc.perform(get("/admin/mentors/review").param("mentor", "alex-m"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "admin/mentor_review :: content"))
                .andExpect(model().attribute("defaultMentorSlug", "alex-m"));
    }
}
