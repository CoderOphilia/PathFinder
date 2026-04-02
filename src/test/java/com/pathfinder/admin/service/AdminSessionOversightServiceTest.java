package com.pathfinder.admin.service;

import com.pathfinder.session.web.DemoSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSessionOversightServiceTest {

    private DemoSessionStore demoSessionStore;
    private AdminSessionOversightService adminSessionOversightService;

    @BeforeEach
    void setUp() {
        demoSessionStore = new DemoSessionStore();
        adminSessionOversightService = new AdminSessionOversightService(demoSessionStore);
    }

    @Test
    void listRequestsReturnsActionableRowsForActiveRequests() {
        DemoSessionStore.SessionRequestView request = createRequest();

        AdminSessionOversightService.SessionOversightItemView item = adminSessionOversightService.listRequests().stream()
                .filter(candidate -> candidate.requestId().equals(request.requestId()))
                .findFirst()
                .orElseThrow();

        assertEquals("Requested", item.statusLabel());
        assertTrue(item.canCancel());
    }

    @Test
    void cancelRequestCancelsActiveRequestAndRejectsTerminalRequest() {
        DemoSessionStore.SessionRequestView request = createRequest();

        adminSessionOversightService.cancelRequest(request.requestId());

        AdminSessionOversightService.SessionOversightItemView cancelledItem = adminSessionOversightService.listRequests().stream()
                .filter(candidate -> candidate.requestId().equals(request.requestId()))
                .findFirst()
                .orElseThrow();

        assertEquals("Cancelled", cancelledItem.statusLabel());
        assertFalse(cancelledItem.canCancel());
        assertThrows(IllegalStateException.class, () -> adminSessionOversightService.cancelRequest(request.requestId()));
    }

    private DemoSessionStore.SessionRequestView createRequest() {
        DemoSessionStore.MentorDirectoryItemView mentor = demoSessionStore.getMentors().getFirst();
        DemoSessionStore.AvailabilitySlotView slot = demoSessionStore.getAvailabilityForMentor(mentor.name()).getFirst();
        return demoSessionStore.createRequest(
                mentor.name(),
                slot.slotId(),
                "Mock interview",
                "Practice behavioral answers",
                "Focus on concise delivery"
        );
    }
}
