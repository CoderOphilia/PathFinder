package com.pathfinder.web;

import java.time.Duration;

import com.pathfinder.admin.web.AdminController;
import com.pathfinder.auth.web.AuthController;
import com.pathfinder.landing.web.LandingController;
import com.pathfinder.mentor.web.DemoMentorCatalog;
import com.pathfinder.mentor.web.MentorController;
import com.pathfinder.mentor.web.MentorPublicController;
import com.pathfinder.seeker.web.SeekerController;
import com.pathfinder.session.web.DemoSessionStore;
import com.pathfinder.session.web.SessionController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({
        LandingController.class,
        AuthController.class,
        SeekerController.class,
        MentorController.class,
        MentorPublicController.class,
        AdminController.class,
        SessionController.class
})
@Import({DemoSessionStore.class, DemoMentorCatalog.class})
class PageRoutingWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemoSessionStore demoSessionStore;

    @BeforeEach
    void resetStore() {
        demoSessionStore.reset();
    }

    @Test
    void getRoutesRenderLayoutWithExpectedFragments() throws Exception {
        assertLayoutPage("/", "landing/index :: content", "fragments/navbar :: navbar");
        assertLayoutPage("/auth/login", "auth/login :: content", "fragments/navbar :: navbar");
        assertLayoutPage("/auth/signup", "auth/signup :: content", "fragments/navbar :: navbar");
        assertLayoutPage("/auth/forgot", "auth/forgot :: content", "fragments/navbar :: navbar");
        assertLayoutPage("/seeker/home", "seeker/home :: content", "fragments/navbar_seeker :: navbar");
        assertLayoutPage("/seeker/profile", "seeker/profile :: content", "fragments/navbar_seeker :: navbar");
        assertLayoutPage("/seeker/mentors", "seeker/mentors :: content", "fragments/navbar_seeker :: navbar");
        assertLayoutPage("/seeker/sessions/new", "seeker/session_new :: content", "fragments/navbar_seeker :: navbar");
        assertLayoutPage("/mentor/home", "mentor/home :: content", "fragments/navbar_mentor :: navbar");
        assertLayoutPage("/mentor/profile", "mentor/profile :: content", "fragments/navbar_mentor :: navbar");
        assertLayoutPage("/mentor/availability", "mentor/availability :: content", "fragments/navbar_mentor :: navbar");
        assertLayoutPage("/mentors/priya-k", "mentor/public_profile :: content", "fragments/navbar :: navbar");
        assertLayoutPage("/mentor/requests", "mentor/requests :: content", "fragments/navbar_mentor :: navbar");
        assertLayoutPage("/admin/home", "admin/home :: content", "fragments/navbar_admin :: navbar");
        assertLayoutPage("/admin/mentors/review", "admin/mentor_review :: content", "fragments/navbar_admin :: navbar");
        assertLayoutPage("/admin/profile", "admin/profile :: content", "fragments/navbar_admin :: navbar");
    }

    @Test
    void loginPostRedirectsByRole() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .param("username", "a@example.com")
                        .param("password", "Password123!")
                        .param("role", "seeker"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seeker/home"))
                .andExpect(flash().attributeExists("flashMessage"));

        mockMvc.perform(post("/auth/login")
                        .param("username", "a@example.com")
                        .param("password", "Password123!")
                        .param("role", "mentor"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentor/home"))
                .andExpect(flash().attributeExists("flashMessage"));

        mockMvc.perform(post("/auth/login")
                        .param("username", "a@example.com")
                        .param("password", "Password123!")
                        .param("role", "admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/home"))
                .andExpect(flash().attributeExists("flashMessage"));
    }

    @Test
    void loginPostValidatesRequiredFields() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .param("username", "")
                        .param("password", "")
                        .param("role", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"))
                .andExpect(flash().attributeExists("formError"));
    }

    @Test
    void signupPostRedirectsByRoleWhenValid() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .param("firstName", "Demo")
                        .param("lastName", "User")
                        .param("email", "demo@example.com")
                        .param("password", "Password123!")
                        .param("confirmPassword", "Password123!")
                        .param("role", "mentor"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentor/home"))
                .andExpect(flash().attributeExists("flashMessage"));
    }

    @Test
    void signupPostValidatesConfirmationAndLength() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .param("firstName", "Demo")
                        .param("lastName", "User")
                        .param("email", "demo@example.com")
                        .param("password", "short")
                        .param("confirmPassword", "short")
                        .param("role", "seeker"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/signup"))
                .andExpect(flash().attributeExists("formError"));

        mockMvc.perform(post("/auth/signup")
                        .param("firstName", "Demo")
                        .param("lastName", "User")
                        .param("email", "demo@example.com")
                        .param("password", "Password123!")
                        .param("confirmPassword", "Mismatch123!")
                        .param("role", "seeker"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/signup"))
                .andExpect(flash().attributeExists("formError"));
    }

    @Test
    void forgotPostHandlesValidAndInvalidEmail() throws Exception {
        mockMvc.perform(post("/auth/forgot").param("email", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/forgot"))
                .andExpect(flash().attributeExists("formError"));

        mockMvc.perform(post("/auth/forgot").param("email", "demo@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"))
                .andExpect(flash().attributeExists("flashMessage"));
    }

    @Test
    void seekerProfilePostValidatesAndRedirects() throws Exception {
        mockMvc.perform(post("/seeker/profile")
                        .param("fullName", "")
                        .param("email", "")
                        .param("targetRole", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seeker/profile"))
                .andExpect(flash().attributeExists("formError"));

        mockMvc.perform(post("/seeker/profile")
                        .param("fullName", "Seeker User")
                        .param("email", "seeker@example.com")
                        .param("targetRole", "Backend Engineer"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seeker/profile"))
                .andExpect(flash().attributeExists("flashMessage"));
    }

    @Test
    void seekerMentorsPageExposesFilterModel() throws Exception {
        mockMvc.perform(get("/seeker/mentors")
                        .param("q", "java")
                        .param("industry", "Technology")
                        .param("interviewCompany", "Google"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "seeker/mentors :: content"))
                .andExpect(model().attribute("query", "java"))
                .andExpect(model().attribute("selectedIndustry", "Technology"))
                .andExpect(model().attribute("selectedCompany", "Google"))
                .andExpect(model().attributeExists("filteredMentors"))
                .andExpect(model().attributeExists("industries"))
                .andExpect(model().attributeExists("interviewCompanies"));
    }

    @Test
    void mentorRequestsPageExposesPendingAndPreviousLists() throws Exception {
        mockMvc.perform(get("/mentor/requests"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "mentor/requests :: content"))
                .andExpect(model().attributeExists("pendingRequests"))
                .andExpect(model().attributeExists("previousRequests"));
    }

    @Test
    void adminMentorReviewPageExposesTableData() throws Exception {
        mockMvc.perform(get("/admin/mentors/review").param("mentor", "alex-m"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "admin/mentor_review :: content"))
                .andExpect(model().attributeExists("reviewItems"))
                .andExpect(model().attribute("defaultMentorSlug", "alex-m"));
    }

    @Test
    void adminMentorReviewDetailsRendersFrameTemplate() throws Exception {
        mockMvc.perform(get("/admin/mentors/review/details/alex-m"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/mentor_review_detail_frame"))
                .andExpect(model().attributeExists("selectedReviewItem"))
                .andExpect(model().attributeExists("selectedMentor"));
    }

    @Test
    void validSessionRequestRedirectsToDetailPage() throws Exception {
        String slotId = slotIdForMentor("Priya K.", 0);
        mockMvc.perform(post("/seeker/sessions")
                        .param("mentorName", "Priya K.")
                        .param("slotId", slotId)
                        .param("sessionType", "Mock interview")
                        .param("objective", "Practice behavioral answers")
                        .param("bookingNotes", "Focus on STAR examples"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/seeker/sessions/REQ-*"))
                .andExpect(flash().attributeExists("flashMessage"));
    }

    @Test
    void invalidSessionRequestRedirectsToFormWithError() throws Exception {
        mockMvc.perform(post("/seeker/sessions")
                        .param("mentorName", "Priya K.")
                        .param("slotId", "")
                        .param("sessionType", "Mock interview")
                        .param("objective", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/seeker/sessions/new*"))
                .andExpect(flash().attributeExists("formError"));
    }

    @Test
    void unknownSessionRequestRedirectsToMentorList() throws Exception {
        mockMvc.perform(get("/seeker/sessions/REQ-9999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seeker/mentors"))
                .andExpect(flash().attributeExists("formError"));
    }

    @Test
    void mentorDecisionUpdatesRequestStatus() throws Exception {
        String requestId = createSessionRequest("Priya K.", 0);

        mockMvc.perform(post("/mentor/requests/" + requestId + "/decision")
                        .param("decision", "approve")
                        .param("mentorNote", "Looks good. See you then."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentor/requests"))
                .andExpect(flash().attributeExists("flashMessage"));
    }

    @Test
    void paymentPageRendersForApprovedPendingPaymentRequest() throws Exception {
        String requestId = createSessionRequest("Priya K.", 0);
        approveRequest(requestId);

        mockMvc.perform(get("/seeker/sessions/" + requestId + "/payment"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "seeker/session_payment :: content"))
                .andExpect(model().attributeExists("sessionRequest"))
                .andExpect(model().attributeExists("quotedAmountLabel"));
    }

    @Test
    void paymentPreviewFlowRendersWithoutApproval() throws Exception {
        String requestId = createSessionRequest("Priya K.", 0);

        mockMvc.perform(get("/seeker/sessions/" + requestId + "/payment").param("preview", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "seeker/session_payment :: content"))
                .andExpect(model().attribute("previewMode", true));
    }

    @Test
    void paymentPreviewCompleteRedirectsWithoutMutation() throws Exception {
        String requestId = createSessionRequest("Priya K.", 0);

        mockMvc.perform(post("/seeker/sessions/" + requestId + "/payment/preview-complete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seeker/sessions/" + requestId))
                .andExpect(flash().attributeExists("flashMessage"));

        DemoSessionStore.SessionRequestView request = demoSessionStore.findRequest(requestId).orElseThrow();
        assertEquals(DemoSessionStore.SessionStatus.REQUESTED, request.status());
        assertEquals(DemoSessionStore.PaymentStatus.NOT_STARTED, request.paymentStatus());
    }

    @Test
    void paymentPostRequiresApprovedRequest() throws Exception {
        String requestId = createSessionRequest("Priya K.", 0);

        mockMvc.perform(post("/seeker/sessions/" + requestId + "/payment")
                        .param("paymentMethod", "card_visa_demo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seeker/sessions/" + requestId))
                .andExpect(flash().attributeExists("formError"));
    }

    @Test
    void endToEndSessionFlowShowsPaidStatus() throws Exception {
        String requestId = createSessionRequest("Priya K.", 0);
        approveRequest(requestId);
        payRequest(requestId);

        mockMvc.perform(get("/seeker/sessions/" + requestId))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "seeker/session_detail :: content"))
                .andExpect(model().attribute("statusLabel", "Approved - paid"))
                .andExpect(model().attribute("paymentStatusLabel", "Paid"))
                .andExpect(model().attributeExists("sessionRequest"));
    }

    @Test
    void doubleBookingPreventsSecondRequestForSameSlot() throws Exception {
        String slotId = slotIdForMentor("Priya K.", 0);
        createSessionRequest("Priya K.", 0);

        mockMvc.perform(post("/seeker/sessions")
                        .param("mentorName", "Priya K.")
                        .param("slotId", slotId)
                        .param("sessionType", "Mock interview")
                        .param("objective", "Second booking attempt")
                        .param("bookingNotes", "Attempt duplicate"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/seeker/sessions/new*"))
                .andExpect(flash().attributeExists("formError"));
    }

    @Test
    void pendingLockExpiresAfter24HoursAndSlotReopens() throws Exception {
        String slotId = slotIdForMentor("Priya K.", 1);
        String requestId = createSessionRequest("Priya K.", 1);

        demoSessionStore.advanceTime(Duration.ofHours(25));

        mockMvc.perform(get("/seeker/sessions/" + requestId))
                .andExpect(status().isOk())
                .andExpect(model().attribute("statusLabel", "Expired"));

        mockMvc.perform(post("/seeker/sessions")
                        .param("mentorName", "Priya K.")
                        .param("slotId", slotId)
                        .param("sessionType", "System design")
                        .param("objective", "Re-book after expiry")
                        .param("bookingNotes", "Same slot reused"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/seeker/sessions/REQ-*"));
    }

    @Test
    void cancellationWithin24HoursAppliesPartialRefund() throws Exception {
        String requestId = createSessionRequest("Priya K.", 0);
        approveRequest(requestId);
        payRequest(requestId);

        mockMvc.perform(post("/seeker/sessions/" + requestId + "/cancel"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seeker/sessions/" + requestId))
                .andExpect(flash().attributeExists("flashMessage"));

        DemoSessionStore.SessionRequestView request = demoSessionStore.findRequest(requestId).orElseThrow();
        assertEquals(DemoSessionStore.SessionStatus.CANCELLED, request.status());
        assertEquals(DemoSessionStore.PaymentStatus.PARTIAL_REFUND, request.paymentStatus());
        assertEquals(50, request.cancellationFeePercent());
    }

    @Test
    void cancellationOutside24HoursAppliesNoFee() throws Exception {
        String requestId = createSessionRequest("Priya K.", 2);
        approveRequest(requestId);
        payRequest(requestId);

        mockMvc.perform(post("/seeker/sessions/" + requestId + "/cancel"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seeker/sessions/" + requestId))
                .andExpect(flash().attributeExists("flashMessage"));

        DemoSessionStore.SessionRequestView request = demoSessionStore.findRequest(requestId).orElseThrow();
        assertEquals(DemoSessionStore.SessionStatus.CANCELLED, request.status());
        assertEquals(DemoSessionStore.PaymentStatus.REFUNDED, request.paymentStatus());
        assertEquals(0, request.cancellationFeePercent());
    }

    @Test
    void mentorCanCancelApprovedSession() throws Exception {
        String requestId = createSessionRequest("Priya K.", 1);
        approveRequest(requestId);

        mockMvc.perform(post("/mentor/sessions/" + requestId + "/cancel"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentor/requests"))
                .andExpect(flash().attributeExists("flashMessage"));

        DemoSessionStore.SessionRequestView request = demoSessionStore.findRequest(requestId).orElseThrow();
        assertEquals(DemoSessionStore.SessionStatus.CANCELLED, request.status());
    }

    @Test
    void mentorCanMarkPaidSessionCompleted() throws Exception {
        String requestId = createSessionRequest("Priya K.", 1);
        approveRequest(requestId);
        payRequest(requestId);

        mockMvc.perform(post("/mentor/sessions/" + requestId + "/complete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentor/requests"))
                .andExpect(flash().attributeExists("flashMessage"));

        DemoSessionStore.SessionRequestView request = demoSessionStore.findRequest(requestId).orElseThrow();
        assertEquals(DemoSessionStore.SessionStatus.COMPLETED, request.status());
    }

    @Test
    void mentorProfilePostValidatesAndRedirects() throws Exception {
        mockMvc.perform(post("/mentor/profile")
                        .param("fullName", "")
                        .param("expertise", "")
                        .param("hourlyRate", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentor/profile"))
                .andExpect(flash().attributeExists("formError"));

        mockMvc.perform(post("/mentor/profile")
                        .param("fullName", "Mentor User")
                        .param("expertise", "Java")
                        .param("hourlyRate", "80")
                        .param("currentTitle", "Senior Engineer")
                        .param("currentCompany", "Example Corp")
                        .param("interviewCompanies", "Amazon, Meta, Stripe"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentor/profile"))
                .andExpect(flash().attributeExists("flashMessage"))
                .andExpect(flash().attributeExists("currentRoleBadge"))
                .andExpect(flash().attributeExists("interviewCompanyBadges"));
    }

    @Test
    void mentorAvailabilityPostValidatesAndRedirects() throws Exception {
        mockMvc.perform(post("/mentor/availability")
                        .param("timezone", "America/Vancouver")
                        .param("slotLengthMinutes", "45")
                        .param("bufferMinutes", "10")
                        .param("bookingNoticeHours", "24"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentor/availability"))
                .andExpect(flash().attributeExists("formError"));

        mockMvc.perform(post("/mentor/availability")
                        .param("timezone", "America/Vancouver")
                        .param("slotLengthMinutes", "45")
                        .param("bufferMinutes", "10")
                        .param("bookingNoticeHours", "24")
                        .param("monEnabled", "on")
                        .param("monStart", "17:00")
                        .param("monEnd", "19:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentor/availability"))
                .andExpect(flash().attributeExists("flashMessage"));
    }

    @Test
    void adminProfilePostValidatesAndRedirects() throws Exception {
        mockMvc.perform(post("/admin/profile")
                        .param("fullName", "")
                        .param("email", "")
                        .param("team", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/profile"))
                .andExpect(flash().attributeExists("formError"));

        mockMvc.perform(post("/admin/profile")
                        .param("fullName", "Admin User")
                        .param("email", "admin@example.com")
                        .param("team", "Operations"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/profile"))
                .andExpect(flash().attributeExists("flashMessage"));
    }

    private void assertLayoutPage(String path, String content, String navbarType) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", content))
                .andExpect(model().attribute("navbarType", navbarType))
                .andExpect(model().attribute("devMode", true))
                .andExpect(model().attributeExists("currentUrl"));
    }

    private String createSessionRequest(String mentorName, int slotIndex) throws Exception {
        String slotId = slotIdForMentor(mentorName, slotIndex);
        MvcResult result = mockMvc.perform(post("/seeker/sessions")
                        .param("mentorName", mentorName)
                        .param("slotId", slotId)
                        .param("sessionType", "Mock interview")
                        .param("objective", "Practice behavioral answers")
                        .param("bookingNotes", "Please focus on leadership examples"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/seeker/sessions/REQ-*"))
                .andReturn();

        String redirectedUrl = result.getResponse().getRedirectedUrl();
        return redirectedUrl.substring(redirectedUrl.lastIndexOf('/') + 1);
    }

    private String slotIdForMentor(String mentorName, int slotIndex) {
        DemoSessionStore.MentorDirectoryItemView mentor = demoSessionStore.getMentorByName(mentorName).orElseThrow();
        return mentor.availability().get(slotIndex).slotId();
    }

    private void approveRequest(String requestId) throws Exception {
        mockMvc.perform(post("/mentor/requests/" + requestId + "/decision")
                        .param("decision", "approve")
                        .param("mentorNote", "Approved for booking."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentor/requests"));
    }

    private void payRequest(String requestId) throws Exception {
        mockMvc.perform(post("/seeker/sessions/" + requestId + "/payment")
                        .param("paymentMethod", "card_visa_demo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seeker/sessions/" + requestId))
                .andExpect(flash().attributeExists("flashMessage"));
    }
}
