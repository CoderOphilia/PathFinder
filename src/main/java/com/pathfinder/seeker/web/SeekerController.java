package com.pathfinder.seeker.web;

import java.util.List;
import java.util.Locale;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.web.AuthController;
import com.pathfinder.mentor.web.DemoMentorCatalog;

import com.pathfinder.seeker.domain.SeekerProfile;
import com.pathfinder.seeker.dto.SeekerProfileRequest;
import com.pathfinder.seeker.service.SeekerProfileService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/seeker")
public class SeekerController {





    private static final String SEEKER_NAVBAR = "fragments/navbar_seeker :: navbar";
    private final DemoMentorCatalog mentorCatalog;
    private final SeekerProfileService seekerProfileService;

    public SeekerController(DemoMentorCatalog mentorCatalog, SeekerProfileService seekerProfileService) {
        this.mentorCatalog = mentorCatalog;
        this.seekerProfileService = seekerProfileService;
    }

    @GetMapping("/home")
    public String home(Model model) {
        return renderPage(model, "Mentee home", "seeker/home :: content");
    }





    @GetMapping("/mentors")
    public String mentors(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String industry,
            @RequestParam(name = "interviewCompany", defaultValue = "") String interviewCompany,
            Model model
    ) {
        List<MentorCard> sampleMentors = mentorCatalog.listMentors().stream()
                .map(this::toMentorCard)
                .toList();

        String selectedIndustry = safeTrim(industry);
        String selectedCompany = safeTrim(interviewCompany);
        String query = safeTrim(q);
        String normalizedQuery = query.toLowerCase(Locale.ROOT);

        List<MentorCard> filteredMentors = sampleMentors.stream()
                .filter(mentor -> selectedIndustry.isEmpty() || mentor.industry().equalsIgnoreCase(selectedIndustry))
                .filter(mentor -> selectedCompany.isEmpty() || mentor.interviewCompanies().stream()
                        .anyMatch(company -> company.equalsIgnoreCase(selectedCompany)))
                .filter(mentor -> normalizedQuery.isEmpty() || matchesQuery(mentor, normalizedQuery))
                .toList();

        List<String> industries = sampleMentors.stream()
                .map(MentorCard::industry)
                .distinct()
                .sorted()
                .toList();

        List<String> interviewCompanies = sampleMentors.stream()
                .flatMap(mentor -> mentor.interviewCompanies().stream())
                .distinct()
                .sorted()
                .toList();

        model.addAttribute("query", query);
        model.addAttribute("selectedIndustry", selectedIndustry);
        model.addAttribute("selectedCompany", selectedCompany);
        model.addAttribute("filteredMentors", filteredMentors);
        model.addAttribute("industries", industries);
        model.addAttribute("interviewCompanies", interviewCompanies);
        return renderPage(model, "Find mentors", "seeker/mentors :: content");
    }


    @GetMapping("/profile")
    public String profile(
            @RequestParam(defaultValue = "") String email,
            HttpSession session,
            Model model) {
        String normalizedEmail = resolveCurrentMenteeEmail(session, email);
        if (!normalizedEmail.isEmpty() && !model.containsAttribute("email")) {
            model.addAttribute("email", normalizedEmail);
        }
        if (!normalizedEmail.isEmpty()) {
            populateProfileForm(model, normalizedEmail);
        }
        return renderPage(model, "Mentee profile", "seeker/profile :: content");
    }

    @PostMapping("/profile")
    public  String saveProfile(
            @AuthenticationPrincipal UserDetails userDetails,  // from Spring Security
            @RequestBody @Valid SeekerProfileRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            seekerProfileService.saveSeekerProfile(userDetails.getUsername(), request);
            redirectAttributes.addFlashAttribute("flashMessage", "Profile saved successfully.");
            return "redirect:/seeker/profile";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/seeker/profile/edit";
        }
    }


    private String renderPage(Model model, String title, String content) {
        model.addAttribute("title", title);
        model.addAttribute("navbarType", SEEKER_NAVBAR);
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
        String normalizedFallback = normalizeText(fallbackEmail);
        if (!normalizedFallback.isEmpty()) {
            return normalizedFallback;
        }
        Object sessionEmail = session.getAttribute(AuthController.SESSION_USER_EMAIL);
        Object sessionRole = session.getAttribute(AuthController.SESSION_USER_ROLE);
        if (sessionEmail == null || sessionRole == null) {
            return "";
        }
        if (!"mentee".equalsIgnoreCase(sessionRole.toString())) {
            return "";
        }
        return normalizeText(sessionEmail.toString());
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private void populateProfileForm(Model model, String email) {
        User menteeUser = seekerProfileService.findMenteeUserByEmail(email);

        if (menteeUser != null && !model.containsAttribute("fullName")) {
            model.addAttribute("fullName", buildFullName(menteeUser.getFirstName(), menteeUser.getLastName()));
        }

        SeekerProfile profile = seekerProfileService.findProfileByEmail(email);
        if (profile == null) {
            return;
        }
        if(!model.containsAttribute("email"))
        {
            model.addAttribute("email", menteeUser.getEmail());

        }

        if (!model.containsAttribute("targetRole"))
        {
            model.addAttribute("targetRole", profile.getTargetRole());
        }

        if (!model.containsAttribute("experienceLevel")) {
            model.addAttribute("experienceLevel",profile.getExperienceLevel());
        }

        if(!model.containsAttribute("timezone")) {
            model.addAttribute("timezone", profile.getTimezone());
        }
        if(!model.containsAttribute("currentGoals")) {
            model.addAttribute("currentGoals", profile.getCurrentGoals());
        }

    }
    private String buildFullName(String firstName, String lastName) {
        String combined = (normalizeText(firstName) + " " + normalizeText(lastName)).trim();
        return combined;
    }


}
