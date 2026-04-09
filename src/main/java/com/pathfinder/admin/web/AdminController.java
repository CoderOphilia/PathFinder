package com.pathfinder.admin.web;

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
        // Home only shows summary counts and links to the admin tools.
        model.addAttribute("pendingMentorReviewCount", adminReviewService.pendingReviewCount());
        model.addAttribute("managedUserCount", adminAccountService.totalUserCount());
        model.addAttribute("activeSessionCount", adminSessionOversightService.activeSessionCount());
        return renderPage(model, "Admin home", "admin/home :: content");
    }

    @GetMapping("/mentors/review")
    public String mentorReview(Model model) {
        model.addAttribute("reviewItems", adminReviewService.listReviewItems());
        return renderPage(model, "Mentor review", "admin/mentor_review :: content");
    }

    @GetMapping("/mentors/review/{mentorSlug}")
    public String mentorReviewDetail(
            @PathVariable String mentorSlug,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        AdminReviewService.MentorReviewDetailView selectedReviewItem = adminReviewService.findReviewItem(mentorSlug);
        if (selectedReviewItem == null) {
            redirectAttributes.addFlashAttribute("formError", "Mentor review item not found.");
            return "redirect:/admin/mentors/review";
        }
        model.addAttribute("selectedReviewItem", selectedReviewItem);
        return renderPage(model, "Mentor review detail", "admin/mentor_review_detail :: content");
    }

    @PostMapping("/mentors/review/{mentorSlug}/approve")
    public String approveMentor(
            @PathVariable String mentorSlug,
            @RequestParam(defaultValue = "") String adminNote,
            RedirectAttributes redirectAttributes
    ) {
        try {
            adminReviewService.approveMentor(mentorSlug, adminNote);
            redirectAttributes.addFlashAttribute("flashMessage", "Mentor approved.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
        }
        return "redirect:/admin/mentors/review/" + mentorSlug;
    }

    @PostMapping("/mentors/review/{mentorSlug}/deny")
    public String denyMentor(
            @PathVariable String mentorSlug,
            @RequestParam(defaultValue = "") String adminNote,
            RedirectAttributes redirectAttributes
    ) {
        try {
            adminReviewService.denyMentor(mentorSlug, adminNote);
            redirectAttributes.addFlashAttribute("flashMessage", "Mentor denied.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
        }
        return "redirect:/admin/mentors/review/" + mentorSlug;
    }

    @GetMapping("/users")
    public String users(Model model) {
        // Users page only needs the rows prepared by the service.
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
            redirectAttributes.addFlashAttribute("flashMessage", "User suspended.");
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
            redirectAttributes.addFlashAttribute("flashMessage", "User reactivated.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/sessions")
    public String sessions(Model model) {
        // Sessions page only needs the rows prepared by the service.
        model.addAttribute("sessionItems", adminSessionOversightService.listRequests());
        return renderPage(model, "Session oversight", "admin/sessions :: content");
    }

    @PostMapping("/sessions/{requestId}/cancel")
    public String cancelSession(
            @PathVariable Long requestId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            adminSessionOversightService.cancelRequest(requestId);
            redirectAttributes.addFlashAttribute("flashMessage", "Session cancelled.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
        }
        return "redirect:/admin/sessions";
    }

    private String renderPage(Model model, String title, String content) {
        model.addAttribute("title", title);
        model.addAttribute("navbarType", ADMIN_NAVBAR);
        model.addAttribute("content", content);
        return "layout";
    }
}
