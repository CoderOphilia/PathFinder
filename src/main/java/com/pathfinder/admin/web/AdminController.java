package com.pathfinder.admin.web;

import java.util.List;
import java.util.Optional;

import com.pathfinder.admin.service.AdminAccountService;
import com.pathfinder.admin.service.AdminReviewService;
import com.pathfinder.admin.service.AdminSessionOversightService;
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
    private final AdminReviewService adminReviewService;
    private final AdminAccountService adminAccountService;
    private final AdminSessionOversightService adminSessionOversightService;

    public AdminController(
            AdminReviewService adminReviewService,
            AdminAccountService adminAccountService,
            AdminSessionOversightService adminSessionOversightService
    ) {
        this.adminReviewService = adminReviewService;
        this.adminAccountService = adminAccountService;
        this.adminSessionOversightService = adminSessionOversightService;
    }

    @GetMapping("/home")
    public String home(Model model) {
        model.addAttribute("pendingMentorReviewCount", adminReviewService.pendingReviewCount());
        model.addAttribute("managedUserCount", adminAccountService.totalUserCount());
        model.addAttribute("activeSessionCount", adminSessionOversightService.activeSessionCount());
        return renderPage(model, "Admin home", "admin/home :: content");
    }

    @GetMapping("/mentors/review")
    public String mentorReview(
            @RequestParam(defaultValue = "") String mentor,
            Model model
    ) {
        List<AdminReviewService.MentorReviewItemView> reviewItems = adminReviewService.listReviewItems();
        AdminReviewService.MentorReviewItemView selectedReviewItem = selectReviewItem(reviewItems, mentor);
        String defaultMentorSlug = selectedReviewItem == null ? "" : selectedReviewItem.mentor().slug();
        model.addAttribute("reviewItems", reviewItems);
        model.addAttribute("selectedReviewItem", selectedReviewItem);
        model.addAttribute("selectedMentor", selectedReviewItem == null ? null : selectedReviewItem.mentor());
        model.addAttribute("defaultMentorSlug", defaultMentorSlug);
        model.addAttribute(
                "defaultMentorDetailPath",
                isBlank(defaultMentorSlug) ? "" : "/admin/mentors/review/details/" + defaultMentorSlug
        );
        return renderPage(model, "Mentor review", "admin/mentor_review :: content");
    }

    @GetMapping("/mentors/review/details/{mentorSlug}")
    public String mentorReviewDetails(
            @PathVariable String mentorSlug,
            Model model
    ) {
        Optional<AdminReviewService.MentorReviewItemView> selectedReviewItem = adminReviewService.findReviewItem(mentorSlug);
        if (selectedReviewItem.isEmpty()) {
            return "redirect:/admin/mentors/review";
        }

        model.addAttribute("selectedReviewItem", selectedReviewItem.get());
        model.addAttribute("selectedMentor", selectedReviewItem.get().mentor());
        return "admin/mentor_review_detail_frame";
    }

    @PostMapping("/mentors/review/{mentorSlug}/approve")
    public String approveMentor(
            @PathVariable String mentorSlug,
            @RequestParam(defaultValue = "") String adminNote,
            RedirectAttributes redirectAttributes
    ) {
        try {
            adminReviewService.approveMentor(mentorSlug, adminNote);
            redirectAttributes.addFlashAttribute("flashMessage", "Mentor verification approved.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
        }
        return "redirect:/admin/mentors/review?mentor=" + mentorSlug;
    }

    @PostMapping("/mentors/review/{mentorSlug}/request-updates")
    public String requestMentorUpdates(
            @PathVariable String mentorSlug,
            @RequestParam(defaultValue = "") String adminNote,
            RedirectAttributes redirectAttributes
    ) {
        try {
            adminReviewService.requestUpdates(mentorSlug, adminNote);
            redirectAttributes.addFlashAttribute("flashMessage", "Profile updates requested from mentor.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
        }
        return "redirect:/admin/mentors/review?mentor=" + mentorSlug;
    }

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("managedUsers", adminAccountService.listUsers());
        return renderPage(model, "User moderation", "admin/users :: content");
    }

    @PostMapping("/users/{userId}/suspend")
    public String suspendUser(
            @PathVariable Long userId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            adminAccountService.suspendUser(userId);
            redirectAttributes.addFlashAttribute("flashMessage", "User account suspended.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{userId}/reactivate")
    public String reactivateUser(
            @PathVariable Long userId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            adminAccountService.reactivateUser(userId);
            redirectAttributes.addFlashAttribute("flashMessage", "User account reactivated.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/sessions")
    public String sessions(Model model) {
        model.addAttribute("sessionItems", adminSessionOversightService.listRequests());
        return renderPage(model, "Session oversight", "admin/sessions :: content");
    }

    @PostMapping("/sessions/{requestId}/cancel")
    public String cancelSession(
            @PathVariable String requestId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            adminSessionOversightService.cancelRequest(requestId);
            redirectAttributes.addFlashAttribute("flashMessage", "Session request cancelled from admin oversight.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
        }
        return "redirect:/admin/sessions";
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

        redirectAttributes.addFlashAttribute("flashMessage", "Admin profile saved.");
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

    private AdminReviewService.MentorReviewItemView selectReviewItem(
            List<AdminReviewService.MentorReviewItemView> reviewItems,
            String selectedSlug
    ) {
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
}
