package com.pathfinder.seeker.web;

import java.util.List;
import java.util.Locale;

import com.pathfinder.mentor.web.DemoMentorCatalog;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/seeker")
public class SeekerController {

    private static final String SEEKER_NAVBAR = "fragments/navbar_seeker :: navbar";
    private final DemoMentorCatalog mentorCatalog;

    public SeekerController(DemoMentorCatalog mentorCatalog) {
        this.mentorCatalog = mentorCatalog;
    }

    @GetMapping("/home")
    public String home(Model model) {
        return renderPage(model, "Mentee home", "seeker/home :: content");
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        return renderPage(model, "Mentee profile", "seeker/profile :: content");
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

    @PostMapping("/profile")
    public String saveProfile(
            @RequestParam(defaultValue = "") String fullName,
            @RequestParam(defaultValue = "") String email,
            @RequestParam(defaultValue = "") String targetRole,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(fullName) || isBlank(email) || isBlank(targetRole)) {
            redirectAttributes.addFlashAttribute("formError", "Name, email, and target role are required.");
            return "redirect:/seeker/profile";
        }

        redirectAttributes.addFlashAttribute("flashMessage", "Mentee profile saved (demo mode).");
        return "redirect:/seeker/profile";
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
}
