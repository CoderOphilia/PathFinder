package com.pathfinder.web;

import com.pathfinder.admin.service.AdminAccountService;
import com.pathfinder.admin.service.AdminReviewService;
import com.pathfinder.admin.service.AdminSessionOversightService;
import com.pathfinder.auth.config.SecurityConfig;
import com.pathfinder.auth.config.SessionRoleAuthenticationFilter;
import com.pathfinder.auth.config.UploadResourceConfig;
import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.service.UserService;
import com.pathfinder.auth.web.AuthController;
import com.pathfinder.landing.web.LandingController;
import com.pathfinder.mentee.domain.MenteeProfile;
import com.pathfinder.mentee.dto.CalenderDay;
import com.pathfinder.mentee.dto.MentorDirectoryItemView;
import com.pathfinder.mentee.service.MenteeProfileService;
import com.pathfinder.mentee.dto.CalendarEvent;
import com.pathfinder.mentor.service.MentorProfileService;
import com.pathfinder.mentor.web.MentorController;
import com.pathfinder.mentor.web.MentorPublicController;
import com.pathfinder.mentee.web.MenteeController;
import com.pathfinder.profile.service.ProfileImageStorageService;
import com.pathfinder.session.domain.SessionRequest;
import com.pathfinder.session.domain.SessionStatus;
import com.pathfinder.session.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({
        LandingController.class,
        AuthController.class,
        MenteeController.class,
        MentorController.class,
        MentorPublicController.class,
        com.pathfinder.admin.web.AdminController.class,
        com.pathfinder.session.web.SessionController.class
})
@Import({SecurityConfig.class, SessionRoleAuthenticationFilter.class, UploadResourceConfig.class})
class PageRoutingWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private MenteeProfileService menteeProfileService;

    @MockBean
    private MentorProfileService mentorProfileService;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private AdminReviewService adminReviewService;

    @MockBean
    private AdminAccountService adminAccountService;

    @MockBean
    private AdminSessionOversightService adminSessionOversightService;

    @MockBean
    private ProfileImageStorageService profileImageStorageService;

    @BeforeEach
    void setUp() {
        when(userService.findUserByEmail("mentee@example.com")).thenReturn(createUser("mentee@example.com", "mentee"));
        when(userService.findUserByEmail("mentor@example.com")).thenReturn(createUser("mentor@example.com", "mentor"));
        when(userService.findUserByEmail("admin@example.com")).thenReturn(createUser("admin@example.com", "admin"));
        when(userService.isAccountActive(any(User.class))).thenReturn(true);
        when(menteeProfileService.getNextSession(anyString())).thenReturn(Optional.empty());
        when(menteeProfileService.getLatestCompletedSession(anyString())).thenReturn(Optional.empty());
        when(menteeProfileService.getMenteeSession(anyString())).thenReturn(List.of());
        when(menteeProfileService.getPendingCount(anyString())).thenReturn(0L);
        when(menteeProfileService.buildCalenderDays(any())).thenReturn(List.of(
                new CalenderDay("Mon", List.of(new CalendarEvent("No sessions")))
        ));
        when(menteeProfileService.searchFilterMentors(anyString(), anyString())).thenReturn(List.of());
        when(menteeProfileService.getCompaniesList(anyString())).thenReturn(List.of("Google"));
        when(mentorProfileService.listPublicMentors()).thenReturn(List.of(samplePublicMentorProfile()));
        when(mentorProfileService.findAvailabilityByMentorName(anyString())).thenReturn(List.of(
                new MentorProfileService.AvailabilityInput(2, "18:00", "19:00")
        ));
        when(mentorProfileService.findMentorEmailByName(anyString())).thenReturn("mentor@example.com");
        when(mentorProfileService.findPublicProfileBySlug("priya-k")).thenReturn(samplePublicMentorProfile());
        when(sessionService.getBookingPolicy(anyString(), anyString()))
                .thenReturn(new SessionService.BookingPolicy(false, false, false, false, 0, 2));
        when(sessionService.getSessionsForMentor(anyString())).thenReturn(List.of());
        when(adminReviewService.pendingReviewCount()).thenReturn(0L);
        when(adminAccountService.totalUserCount()).thenReturn(0L);
        when(adminSessionOversightService.activeSessionCount()).thenReturn(0L);
    }

    @Test
    void getRoutesRenderLayoutWithExpectedFragments() throws Exception {
        assertLayoutPage("/", "landing/index :: content", "fragments/navbar :: navbar");
        assertLayoutPage("/auth/login", "auth/login :: content", "fragments/navbar :: navbar");
        assertLayoutPage("/auth/signup", "auth/signup :: content", "fragments/navbar :: navbar");
        assertLayoutPage("/auth/forgot", "auth/forgot :: content", "fragments/navbar :: navbar");
        assertLayoutPage("/mentors/priya-k", "mentor/public_profile :: content", "fragments/navbar :: navbar");
        assertAuthenticatedLayoutPage("/mentee/home", "mentee/home :: content", "fragments/navbar_mentee :: navbar", "mentee@example.com", "mentee");
        assertAuthenticatedLayoutPage("/mentee/mentors", "mentee/mentors :: content", "fragments/navbar_mentee :: navbar", "mentee@example.com", "mentee");
        assertAuthenticatedLayoutPage("/mentee/sessions/new", "mentee/session_new :: content", "fragments/navbar_mentee :: navbar", "mentee@example.com", "mentee");
        assertAuthenticatedLayoutPage("/mentor/home", "mentor/home :: content", "fragments/navbar_mentor :: navbar", "mentor@example.com", "mentor");
        assertAuthenticatedLayoutPage("/mentor/profile", "mentor/profile :: content", "fragments/navbar_mentor :: navbar", "mentor@example.com", "mentor");
        assertAuthenticatedLayoutPage("/mentor/availability", "mentor/availability :: content", "fragments/navbar_mentor :: navbar", "mentor@example.com", "mentor");
        assertAuthenticatedLayoutPage("/mentor/requests", "mentor/requests :: content", "fragments/navbar_mentor :: navbar", "mentor@example.com", "mentor");
        assertAuthenticatedLayoutPage("/admin/home", "admin/home :: content", "fragments/navbar_admin :: navbar", "admin@example.com", "admin");
    }

    @Test
    void loginPostRedirectsByRole() throws Exception {
        User user = createUser("a@example.com", "mentor");
        user.setPassword("hashed");
        when(userService.findUserByEmail("a@example.com")).thenReturn(user);
        when(userService.passwordMatches("Password123!", "hashed")).thenReturn(true);

        mockMvc.perform(post("/auth/login")
                        .param("email", "a@example.com")
                        .param("password", "Password123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentor/home"))
                .andExpect(flash().attributeExists("flashMessage"));
    }

    @Test
    void signupPostShowsValidationMessageWhenCreationFails() throws Exception {
        when(userService.emailExists("demo@example.com")).thenReturn(false);
        when(userService.createUser(any(User.class))).thenThrow(new IllegalArgumentException("A user with this email already exists"));

        mockMvc.perform(multipart("/auth/signup")
                        .file(new MockMultipartFile("profileImageFile", new byte[0]))
                        .param("firstName", "Demo")
                        .param("lastName", "User")
                        .param("email", "demo@example.com")
                        .param("password", "Password123!")
                        .param("confirmPassword", "Password123!")
                        .param("role", "mentor"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/signup"))
                .andExpect(flash().attribute("formError", "A user with this email already exists"));
    }

    @Test
    void mentorSignupRedirectsToProfileWhenValid() throws Exception {
        User created = createUser("demo@example.com", "mentor");
        when(userService.emailExists("demo@example.com")).thenReturn(false);
        when(userService.createUser(any(User.class))).thenReturn(created);

        mockMvc.perform(multipart("/auth/signup")
                        .file(new MockMultipartFile("profileImageFile", new byte[0]))
                        .param("firstName", "Demo")
                        .param("lastName", "User")
                        .param("email", "demo@example.com")
                        .param("password", "Password123!")
                        .param("confirmPassword", "Password123!")
                        .param("role", "mentor"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentor/profile"))
                .andExpect(flash().attributeExists("flashMessage"));

        verify(mentorProfileService).initializeProfileForNewMentor(1L);
    }

    @Test
    void menteeSignupRedirectsToHomeWhenValid() throws Exception {
        User created = createUser("demo@example.com", "mentee");
        when(userService.emailExists("demo@example.com")).thenReturn(false);
        when(userService.createUser(any(User.class))).thenReturn(created);

        mockMvc.perform(multipart("/auth/signup")
                        .file(new MockMultipartFile("profileImageFile", new byte[0]))
                        .param("firstName", "Demo")
                        .param("lastName", "User")
                        .param("email", "demo@example.com")
                        .param("password", "Password123!")
                        .param("confirmPassword", "Password123!")
                        .param("role", "mentee"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentee/home"))
                .andExpect(flash().attributeExists("flashMessage"));
    }

    @Test
    void menteeProfilePostRedirectsWhenValid() throws Exception {
        User mentee = createUser("mentee@example.com", "mentee");
        when(menteeProfileService.findUserbyEmail("mentee@example.com")).thenReturn(Optional.of(mentee));
        when(menteeProfileService.saveMenteeProfile(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new MenteeProfile());

        mockMvc.perform(multipart("/mentee/profile")
                        .file(new MockMultipartFile("profileImageFile", new byte[0]))
                        .sessionAttr(AuthController.SESSION_USER_EMAIL, "mentee@example.com")
                        .sessionAttr(AuthController.SESSION_USER_ROLE, "mentee")
                        .param("email", "mentee@example.com")
                        .param("targetRole", "Backend Engineer")
                        .param("experienceLevel", "BEGINNER")
                        .param("timezone", "America/Vancouver")
                        .param("currentGoals", "Land my first internship"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentee/profile"))
                .andExpect(flash().attributeExists("flashMessage"));
    }

    @Test
    void validSessionRequestRedirectsToDetailPage() throws Exception {
        SessionRequest request = new SessionRequest();
        request.setId(1L);
        request.setStatus(SessionStatus.REQUESTED);
        when(sessionService.createSession(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(Boolean.class)
        )).thenReturn(request);

        mockMvc.perform(post("/mentee/sessions")
                        .sessionAttr(AuthController.SESSION_USER_EMAIL, "mentee@example.com")
                        .sessionAttr(AuthController.SESSION_USER_ROLE, "mentee")
                        .param("mentorName", "Priya K")
                        .param("slotId", "slot-2-1800-1900")
                        .param("sessionType", "Mock interview")
                        .param("objective", "Practice behavioral answers")
                        .param("bookingNotes", "Focus on STAR examples"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentee/sessions/1"))
                .andExpect(flash().attributeExists("flashMessage"));
    }

    @Test
    void paymentPageRendersForApprovedRequest() throws Exception {
        SessionRequest request = new SessionRequest();
        request.setId(1L);
        request.setStatus(SessionStatus.APPROVED);
        request.setQuotedAmountCents(8000);
        request.setCreatedAt(LocalDateTime.now());
        when(sessionService.getSessionById(1L)).thenReturn(request);

        mockMvc.perform(get("/mentee/sessions/1/payment")
                        .sessionAttr(AuthController.SESSION_USER_EMAIL, "mentee@example.com")
                        .sessionAttr(AuthController.SESSION_USER_ROLE, "mentee"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "mentee/session_payment :: content"))
                .andExpect(model().attributeExists("sessionRequest"))
                .andExpect(model().attributeExists("quotedAmountLabel"));
    }

    @Test
    void unknownSessionRequestRedirectsToMentorList() throws Exception {
        when(sessionService.getSessionById(9999L)).thenReturn(null);

        mockMvc.perform(get("/mentee/sessions/9999")
                        .sessionAttr(AuthController.SESSION_USER_EMAIL, "mentee@example.com")
                        .sessionAttr(AuthController.SESSION_USER_ROLE, "mentee"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentee/mentors"))
                .andExpect(flash().attributeExists("formError"));
    }

    @Test
    void protectedPageRedirectsToLoginWhenSignedOut() throws Exception {
        mockMvc.perform(get("/mentor/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));
    }

    @Test
    void mentorProfileShowsPendingVerificationWithoutSavedProfile() throws Exception {
        mockMvc.perform(get("/mentor/profile")
                        .sessionAttr(AuthController.SESSION_USER_EMAIL, "mentor@example.com")
                        .sessionAttr(AuthController.SESSION_USER_ROLE, "mentor"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "mentor/profile :: content"))
                .andExpect(model().attribute("verificationStatusLabel", "Pending review"))
                .andExpect(model().attribute("verificationStatusMessage", "Your profile is under review."));
    }

    @Test
    void unknownPublicMentorProfileRedirectsToMentorDirectory() throws Exception {
        when(mentorProfileService.findPublicProfileBySlug("pending-mentor")).thenReturn(null);

        mockMvc.perform(get("/mentors/pending-mentor"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentee/mentors"))
                .andExpect(flash().attributeExists("formError"));
    }

    @Test
    void crossRoleAttemptRedirectsToSignedInUsersHome() throws Exception {
        mockMvc.perform(get("/mentor/home")
                        .sessionAttr(AuthController.SESSION_USER_EMAIL, "mentee@example.com")
                        .sessionAttr(AuthController.SESSION_USER_ROLE, "mentee"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mentee/home"));
    }

    @Test
    void logoutClearsSessionAndRedirectsToLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_EMAIL, "mentor@example.com");
        session.setAttribute(AuthController.SESSION_USER_ROLE, "mentor");

        mockMvc.perform(get("/auth/logout").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"))
                .andExpect(flash().attributeExists("flashMessage"));
    }

    private void assertLayoutPage(String path, String content, String navbarType) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", content))
                .andExpect(model().attribute("navbarType", navbarType))
                .andExpect(model().attributeExists("currentUrl"));
    }

    private void assertAuthenticatedLayoutPage(
            String path,
            String content,
            String navbarType,
            String email,
            String role
    ) throws Exception {
        mockMvc.perform(get(path)
                        .sessionAttr(AuthController.SESSION_USER_EMAIL, email)
                        .sessionAttr(AuthController.SESSION_USER_ROLE, role))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", content))
                .andExpect(model().attribute("navbarType", navbarType))
                .andExpect(model().attributeExists("currentUrl"));
    }

    private User createUser(String email, String role) {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setRole(role);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setAccountStatus("ACTIVE");
        return user;
    }

    private MentorProfileService.PublicMentorProfile samplePublicMentorProfile() {
        return new MentorProfileService.PublicMentorProfile(
                "priya-k",
                "Priya K",
                "",
                "Senior Engineer @ Example",
                "$80/hr",
                false,
                "",
                "Backend interview mentor",
                List.of("Java", "Spring"),
                List.of("Google"),
                12
        );
    }
}
