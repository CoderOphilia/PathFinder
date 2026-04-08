package com.pathfinder.mentee.web;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.web.AuthController;
import com.pathfinder.mentee.domain.MenteeProfile;
import com.pathfinder.mentee.dto.MentorDirectoryItemView;
import com.pathfinder.mentee.service.MenteeProfileService;
import com.pathfinder.mentor.domain.MentorProfile;
import com.pathfinder.mentor.web.DemoMentorCatalog;
import com.pathfinder.profile.service.ProfileImageStorageService;
import com.pathfinder.session.domain.SessionRequest;
import com.pathfinder.session.service.SessionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping({"/seeker", "/mentee"})
public class MenteeController {
    private static final String MENTEE_NAVBAR = "fragments/navbar_mentee :: navbar";
//    private final DemoMentorCatalog mentorCatalog;
    private final MenteeProfileService menteeProfileService;
    private  final SessionService sessionService;
    private final ProfileImageStorageService profileImageStorageService;

    public MenteeController(
            MenteeProfileService menteeProfileService,
            SessionService sessionService,
            ProfileImageStorageService profileImageStorageService
    ) {

        this.menteeProfileService = menteeProfileService;
        this.sessionService = sessionService;
        this.profileImageStorageService = profileImageStorageService;

    }

    @GetMapping("/home")
    public String home(Model model, HttpSession session) {
        // Home collects the few session facts the dashboard needs.
        String menteeEmail = resolveCurrentMenteeEmail(session, "");


//
        if (!menteeEmail.isEmpty()){
            menteeProfileService.getNextSession(menteeEmail)
                    .ifPresent(s -> model.addAttribute("nextSession", s));
            menteeProfileService.getLatestCompletedSession(menteeEmail)
                    .ifPresent(s -> model.addAttribute("latestCompletedSession", s));
        }
        List<SessionRequest> menteeSessions = menteeProfileService.getMenteeSession(menteeEmail);
        long pendingRequest = menteeProfileService.getPendingCount(menteeEmail);

        model.addAttribute("pendingRequest",pendingRequest);
        model.addAttribute("menteeSessions", menteeSessions);
        model.addAttribute("calendarDays", menteeProfileService.buildCalenderDays(menteeSessions));

        return renderPage(model, "Mentee home", "mentee/home :: content");
    }

    @GetMapping("/mentors")
    public String mentors(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String industry,
            @RequestParam(name = "interviewCompany", defaultValue = "") String interviewCompany,
            Model model
    ) {

        List <MentorDirectoryItemView> filteredMentors = menteeProfileService.searchFilterMentors(q,interviewCompany);
        List<String> interviewCompanies = menteeProfileService.getCompaniesList(interviewCompany);
        model.addAttribute("size", filteredMentors.size());
        model.addAttribute("filteredMentors", filteredMentors);
        model.addAttribute("query", q);
        model.addAttribute("interviewCompanies", interviewCompanies);
        model.addAttribute("selectedCompany", interviewCompany);

        return renderPage(model, "Find mentors", "mentee/mentors :: content");
    }

    @GetMapping("/profile")
    public String profile(
            @RequestParam(defaultValue = "") String email,
            HttpSession session,
            Model model) {
        String normalizedEmail = resolveCurrentMenteeEmail(session, email);
        if (email == null) return "redirect:/auth/login";

        Optional<User> optionalUser = menteeProfileService.findUserbyEmail(normalizedEmail);
        if (optionalUser.isEmpty()) return "redirect:/auth/login";

        User user = optionalUser.get();
        model.addAttribute("fullName", buildFullName(user.getFirstName(), user.getLastName()));
        model.addAttribute("email", user.getEmail());
        model.addAttribute("profileImageUrl", user.getProfileImageUrl());

        Optional<MenteeProfile> optionalProfile = menteeProfileService.findProfileByUser(user);
        MenteeProfile profile = optionalProfile.orElse(new MenteeProfile());
        model.addAttribute("profile", profile);

        return renderPage(model, "Mentee profile", "mentee/profile :: content");
    }

    @PostMapping("/profile")
    public String saveProfile(
            @RequestParam(required = false) String fullName,
            @RequestParam String email,
            @RequestParam(name = "profileImageFile", required = false) MultipartFile profileImageFile,
            @RequestParam(required = false) String targetRole,
            @RequestParam(required = false) String experienceLevel,
            @RequestParam(required = false) String timezone,
            @RequestParam(required = false) String currentGoals,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String normalizedEmail = resolveCurrentMenteeEmail(session, email);
        Optional<User> optionalUser = menteeProfileService.findUserbyEmail(normalizedEmail);
        User user = optionalUser.get();


        try {
            String profileImageUrl = user.getProfileImageUrl();
            if (profileImageFile != null && !profileImageFile.isEmpty()) {
                profileImageUrl = profileImageStorageService.storeProfileImage(profileImageFile, user.getEmail());
            }
            menteeProfileService.saveMenteeProfile(
                    user.getId(),
                    fullName,
                    profileImageUrl,
                    targetRole,
                    experienceLevel,
                    timezone,
                    currentGoals
            );

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/mentee/profile";
        }
        redirectAttributes.addFlashAttribute("flashMessage", "Profile saved successfully.");
        return "redirect:/mentee/profile";
    }


    private String renderPage(Model model, String title, String content) {
        model.addAttribute("title", title);
        model.addAttribute("navbarType", MENTEE_NAVBAR);
        model.addAttribute("content", content);
        return "layout";
    }

    private MentorCard toMentorCard(DemoMentorCatalog.MentorCatalogItem mentor) {
        return new MentorCard(
                mentor.slug(),
                mentor.name(),
                mentor.roleAtCompany(),
                mentor.rate(),
                mentor.tagline(),
                mentor.industry(),
                mentor.skills(),
                mentor.sessionsCompleted(),
                mentor.interviewCompanies()
        );
    }

    private boolean matchesQuery(MentorCard mentor, String query) {
        return containsIgnoreCase(mentor.name(), query)
                || containsIgnoreCase(mentor.roleAtCompany(), query)
                || containsIgnoreCase(mentor.tagline(), query)
                || containsIgnoreCase(mentor.industry(), query)
                || mentor.skills().stream().anyMatch(skill -> containsIgnoreCase(skill, query))
                || mentor.interviewCompanies().stream().anyMatch(company -> containsIgnoreCase(company, query));
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record MentorCard(
            String slug,
            String name,
            String roleAtCompany,
            String rate,
            String tagline,
            String industry,
            List<String> skills,
            int sessionsCompleted,
            List<String> interviewCompanies
    ) {
    }



    private String resolveCurrentMenteeEmail(HttpSession session, String fallbackEmail) {
        // Prefer the explicit request value, otherwise fall back to the signed-in mentee.
        String normalizedFallback = normalizeText(fallbackEmail);
        if (!normalizedFallback.isEmpty()) {
            return normalizedFallback;
        }
        Object sessionEmail = session.getAttribute(AuthController.SESSION_USER_EMAIL);
        Object sessionRole = session.getAttribute(AuthController.SESSION_USER_ROLE);
        if (sessionEmail == null || sessionRole == null) {
            return "";
        }
        if (!isMenteeRole(sessionRole.toString())) {
            return "";
        }
        return normalizeText(sessionEmail.toString());
    }

    private boolean isMenteeRole(String role) {
        return "mentee".equalsIgnoreCase(role) || "seeker".equalsIgnoreCase(role);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private void populateProfileForm(Model model, String email) {
        User menteeUser = menteeProfileService.findMenteeUserByEmail(email);

        if (menteeUser != null && !model.containsAttribute("fullName")) {
            model.addAttribute("fullName", buildFullName(menteeUser.getFirstName(), menteeUser.getLastName()));
        }

        MenteeProfile profile = menteeProfileService.findProfileByEmail(email);
        if (profile == null) {
            return;
        }
        if (!model.containsAttribute("email")) {
            model.addAttribute("email", menteeUser.getEmail());
        }
        if (!model.containsAttribute("profileImageUrl")) {
            model.addAttribute("profileImageUrl", menteeUser.getProfileImageUrl());
        }

        if (!model.containsAttribute("targetRole")) {
            model.addAttribute("targetRole", profile.getTargetRole());
        }

        if (!model.containsAttribute("experienceLevel")) {
            model.addAttribute("experienceLevel", profile.getExperienceLevel());
        }

        if (!model.containsAttribute("timezone")) {
            model.addAttribute("timezone", profile.getTimezone());
        }
        if (!model.containsAttribute("currentGoals")) {
            model.addAttribute("currentGoals", profile.getCurrentGoals());
        }
    }

    private String buildFullName(String firstName, String lastName) {
        String combined = (normalizeText(firstName) + " " + normalizeText(lastName)).trim();
        return combined;
    }


}
