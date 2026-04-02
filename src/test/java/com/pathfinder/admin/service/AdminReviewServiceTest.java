package com.pathfinder.admin.service;

import com.pathfinder.mentor.web.DemoMentorCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminReviewServiceTest {

    private final AdminReviewService adminReviewService = new AdminReviewService(new DemoMentorCatalog());

    @Test
    void approveMentorUpdatesDisplayedState() {
        adminReviewService.approveMentor("alex-m", "Verified interview history and profile quality.");

        AdminReviewService.MentorReviewItemView item = adminReviewService.findReviewItem("alex-m").orElseThrow();

        assertEquals("Approved", item.reviewStatus());
        assertEquals("Approved", item.statusLabel());
        assertEquals("Verified interview history and profile quality.", item.adminNote());
    }

    @Test
    void requestUpdatesStoresNoteAndChangesState() {
        adminReviewService.requestUpdates("alex-m", "Add stronger evidence for Microsoft interview loop.");

        AdminReviewService.MentorReviewItemView item = adminReviewService.findReviewItem("alex-m").orElseThrow();

        assertEquals("Changes requested", item.reviewStatus());
        assertEquals("Changes requested", item.statusLabel());
        assertTrue(item.adminNote().contains("Microsoft interview loop"));
    }
}
