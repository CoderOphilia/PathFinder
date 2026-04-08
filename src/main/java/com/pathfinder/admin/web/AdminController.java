package com.pathfinder.admin.web;

import com.pathfinder.admin.domain.AdminProfile;
import com.pathfinder.admin.service.AdminAccountService;
import com.pathfinder.admin.service.AdminProfileService;
import com.pathfinder.admin.service.AdminReviewService;
import com.pathfinder.admin.service.AdminSessionOversightService;
import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.web.AuthController;
import jakarta.servlet.http.HttpSession;
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
    private final AdminProfileService adminProfileService;

    public AdminController(
            AdminReviewService adminReviewService,
            AdminAccountService adminAccountService,
            AdminSessionOversightService adminSessionOversightService,
            AdminProfileService adminProfileService
    ) {
        this.adminReviewService = adminReviewService;
        this.adminAccountService = adminAccountService;
        this.adminSessionOversightService = adminSessionOversightService;
        this.adminProfileService = adminProfileService;
    }

    @GetMapping("/home")
    public String home(Model model) {
        // Home only shows summary counts and links to the admin tools.
        model.addAttribute("pendingMentorReviewCount", adminReviewService.pendingReviewCount());
        model.addAttribute("managedUserCount", adminAccountService.totalUserCount());
        model.addAttribute("activeSessionCount", adminSessionOversightService.activeSessionCount());
        return renderPage(model, "Admin home", "admin/home :: content");
    }

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        String adminEmail = resolveCurrentAdminEmail(session);
        if (adminEmail.isEmpty()) {
            model.addAttribute("formError", "Sign in as an admin to edit your profile.");
            return renderPage(model, "Admin profile", "admin/profile :: content");
        }
        populateProfileForm(model, adminEmail);
        return renderPage(model, "Admin profile", "admin/profile :: content");
    }

    @PostMapping("/profile")
    public String saveProfile(
            @RequestParam(defaultValue = "") String team,
            @RequestParam(defaultValue = "") String supportChannel,
            @RequestParam(defaultValue = "") String notes,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String adminEmail = resolveCurrentAdminEmail(session);
        redirectAttributes.addFlashAttribute("team", team);
        redirectAttributes.addFlashAttribute("supportChannel", supportChannel);
        redirectAttributes.addFlashAttribute("notes", notes);

        if (adminEmail.isEmpty()) {
            redirectAttributes.addFlashAttribute("formError", "Sign in as an admin to save your profile.");
            return "redirect:/admin/profile";
        }

        try {
            adminProfileService.saveProfile(adminEmail, team, supportChannel, notes);
            redirectAttributes.addFlashAttribute("flashMessage", "Admin profile saved.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
        }
        return "redirect:/admin/profile";
    }

    @GetMapping("/mentors/review")
    public String mentorReview(
            @RequestParam(defaultValue = "") String mentor,
            Model model
    ) {
        // The controller only loads the rows and the currently selected mentor.
        model.addAttribute("reviewItems", adminReviewService.listReviewItems());
        model.addAttribute("selectedReviewItem", selectReviewItem(mentor));
        return renderPage(model, "Mentor review", "admin/mentor_review :: content");
    }

    @PostMapping("/mentors/review/{mentorSlug}/approve")
    public String approveMentor(
            @PathVariable String mentorSlug,
            @RequestParam(defaultValue = "") String adminNote,
            RedirectAttributes redirectAttributes
    ) {
        // Save the admin decision, then redirect back to the same mentor row.
        try {
            adminReviewService.approveMentor(mentorSlug, adminNote);
            redirectAttributes.addFlashAttribute("flashMessage", "Mentor approved.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
        }
        return "redirect:/admin/mentors/review?mentor=" + mentorSlug;
    }

    @PostMapping("/mentors/review/{mentorSlug}/deny")
    public String denyMentor(
            @PathVariable String mentorSlug,
            @RequestParam(defaultValue = "") String adminNote,
            RedirectAttributes redirectAttributes
    ) {
        // Save the admin decision, then redirect back to the same mentor row.
        try {
            adminReviewService.denyMentor(mentorSlug, adminNote);
            redirectAttributes.addFlashAttribute("flashMessage", "Mentor denied.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
        }
        return "redirect:/admin/mentors/review?mentor=" + mentorSlug;
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

    private void populateProfileForm(Model model, String adminEmail) {
        User adminUser = adminProfileService.findAdminUserByEmail(adminEmail);
        AdminProfile profile = adminProfileService.findProfileByEmail(adminEmail);
        if (adminUser != null) {
            if (!model.containsAttribute("fullName")) {
                model.addAttribute("fullName", buildFullName(adminUser.getFirstName(), adminUser.getLastName(), adminUser.getEmail()));
            }
            if (!model.containsAttribute("email")) {
                model.addAttribute("email", adminUser.getEmail());
            }
        }
        if (!model.containsAttribute("team")) {
            model.addAttribute("team", profile == null ? "" : profile.getTeam());
        }
        if (!model.containsAttribute("supportChannel")) {
            model.addAttribute("supportChannel", profile == null ? "" : profile.getSupportChannel());
        }
        if (!model.containsAttribute("notes")) {
            model.addAttribute("notes", profile == null ? "" : profile.getNotes());
        }
    }

    // Keep selection logic in one place so the template stays simple.
    private AdminReviewService.MentorReviewItemView selectReviewItem(String mentorSlug) {
        if (isBlank(mentorSlug)) {
            return adminReviewService.listReviewItems().stream().findFirst().orElse(null);
        }
        AdminReviewService.MentorReviewItemView selected = adminReviewService.findReviewItem(mentorSlug);
        return selected != null ? selected : adminReviewService.listReviewItems().stream().findFirst().orElse(null);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String resolveCurrentAdminEmail(HttpSession session) {
        Object sessionEmail = session.getAttribute(AuthController.SESSION_USER_EMAIL);
        Object sessionRole = session.getAttribute(AuthController.SESSION_USER_ROLE);
        if (sessionEmail == null || sessionRole == null) {
            return "";
        }
        if (!"admin".equalsIgnoreCase(sessionRole.toString())) {
            return "";
        }
        return normalizeText(sessionEmail.toString());
    }

    private String buildFullName(String firstName, String lastName, String fallbackEmail) {
        String combined = (normalizeText(firstName) + " " + normalizeText(lastName)).trim();
        return combined.isEmpty() ? fallbackEmail : combined;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
