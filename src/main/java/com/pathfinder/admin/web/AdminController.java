package com.pathfinder.admin.web;

import java.util.List;
import java.util.stream.IntStream;

import com.pathfinder.mentor.web.DemoMentorCatalog;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final String ADMIN_NAVBAR = "fragments/navbar_admin :: navbar";
    private final DemoMentorCatalog mentorCatalog;

    public AdminController(DemoMentorCatalog mentorCatalog) {
        this.mentorCatalog = mentorCatalog;
    }

    @GetMapping("/home")
    public String home(Model model) {
        return renderPage(model, "Admin home", "admin/home :: content");
    }

    @GetMapping("/mentors/review")
    public String mentorReview(
            @RequestParam(defaultValue = "") String mentor,
            Model model
    ) {
        List<MentorReviewItem> reviewItems = buildReviewItems();
        MentorReviewItem selectedReviewItem = selectReviewItem(reviewItems, mentor);
        model.addAttribute("reviewItems", reviewItems);
        model.addAttribute("defaultMentorSlug", selectedReviewItem == null ? "" : selectedReviewItem.mentor().slug());
        return renderPage(model, "Mentor review", "admin/mentor_review :: content");
    }

    @GetMapping("/mentors/review/details/{mentorSlug}")
    public String mentorReviewDetails(
            @PathVariable String mentorSlug,
            Model model
    ) {
        List<MentorReviewItem> reviewItems = buildReviewItems();
        MentorReviewItem selectedReviewItem = selectReviewItem(reviewItems, mentorSlug);
        if (selectedReviewItem == null) {
            return "redirect:/admin/mentors/review";
        }

        model.addAttribute("selectedReviewItem", selectedReviewItem);
        model.addAttribute("selectedMentor", selectedReviewItem.mentor());
        return "admin/mentor_review_detail_frame";
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        return renderPage(model, "Admin profile", "admin/profile :: content");
    }

    @PostMapping("/profile")
    public String saveProfile(
            @RequestParam(defaultValue = "") String fullName,
            @RequestParam(defaultValue = "") String email,
            @RequestParam(defaultValue = "") String team,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(fullName) || isBlank(email) || isBlank(team)) {
            redirectAttributes.addFlashAttribute("formError", "Name, email, and team are required.");
            return "redirect:/admin/profile";
        }

        redirectAttributes.addFlashAttribute("flashMessage", "Admin profile saved (demo mode).");
        return "redirect:/admin/profile";
    }

    private String renderPage(Model model, String title, String content) {
        model.addAttribute("title", title);
        model.addAttribute("navbarType", ADMIN_NAVBAR);
        model.addAttribute("content", content);
        return "layout";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private MentorReviewItem selectReviewItem(List<MentorReviewItem> reviewItems, String selectedSlug) {
        if (reviewItems.isEmpty()) {
            return null;
        }
        if (isBlank(selectedSlug)) {
            return reviewItems.getFirst();
        }

        String normalizedSlug = selectedSlug.trim();
        return reviewItems.stream()
                .filter(item -> item.mentor().slug().equalsIgnoreCase(normalizedSlug))
                .findFirst()
                .orElse(reviewItems.getFirst());
    }

    private List<MentorReviewItem> buildReviewItems() {
        List<DemoMentorCatalog.MentorCatalogItem> mentors = mentorCatalog.listMentors();
        List<String> stages = List.of(
                "Credential check",
                "Interview history review",
                "Final approval",
                "Profile quality review",
                "Reference check"
        );
        List<String> submittedLabels = List.of(
                "Feb 17",
                "Feb 16",
                "Feb 15",
                "Feb 14",
                "Feb 13"
        );
        List<String> interviewEvidence = List.of(
                "2 of 3 companies verified",
                "3 of 3 companies verified",
                "1 of 3 companies verified",
                "2 of 2 companies verified",
                "Pending verification response"
        );
        List<String> profileQuality = List.of(
                "Needs stronger expertise summary",
                "Strong profile clarity",
                "Missing company evidence details",
                "Strong profile clarity",
                "Update required on headline"
        );
        List<String> nextSteps = List.of(
                "Request one more credential artifact.",
                "Ready for final decision after quick QA pass.",
                "Block approval until missing evidence is uploaded.",
                "Approve if no policy issues are found.",
                "Request profile headline update and re-review."
        );
        List<String> statusLabels = List.of(
                "Due today",
                "On track",
                "Blocked",
                "On track",
                "Due today"
        );
        List<String> statusClasses = List.of(
                "statusBadge statusBadge--requested",
                "statusBadge statusBadge--approved",
                "statusBadge statusBadge--declined",
                "statusBadge statusBadge--approved",
                "statusBadge statusBadge--requested"
        );
        List<String> notes = List.of(
                "Needs credential document confirmation.",
                "Interview list verified, waiting final note.",
                "Missing company evidence for one interview entry.",
                "Bio and expertise quality checks complete.",
                "Reference email follow-up pending."
        );

        return IntStream.range(0, mentors.size())
                .mapToObj(index -> {
                    int idx = index % stages.size();
                    return new MentorReviewItem(
                            mentors.get(index),
                            stages.get(idx),
                            submittedLabels.get(idx),
                            interviewEvidence.get(idx),
                            profileQuality.get(idx),
                            nextSteps.get(idx),
                            statusLabels.get(idx),
                            statusClasses.get(idx),
                            notes.get(idx)
                    );
                })
                .toList();
    }

    private record MentorReviewItem(
            DemoMentorCatalog.MentorCatalogItem mentor,
            String stage,
            String submittedDate,
            String interviewEvidence,
            String profileQuality,
            String nextStep,
            String statusLabel,
            String statusClass,
            String note
    ) {
    }
}
