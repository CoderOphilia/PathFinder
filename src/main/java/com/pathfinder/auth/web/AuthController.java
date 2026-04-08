package com.pathfinder.auth.web;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.service.UserService;
import com.pathfinder.profile.service.ProfileImageStorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    public static final String SESSION_USER_EMAIL = "sessionUserEmail";
    public static final String SESSION_USER_ROLE = "sessionUserRole";

    private final UserService service;
    private final ProfileImageStorageService profileImageStorageService;

    private static final String PUBLIC_NAVBAR = "fragments/navbar :: navbar";

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("user", new User());
        return renderPage(model, "Sign in", "auth/login :: content");
    }

    @PostMapping("/login")
    public String loginSubmit(
            @ModelAttribute("user") User user,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(user.getEmail()) || isBlank(user.getPassword())) {
            redirectAttributes.addFlashAttribute("formError", "Enter your email and password.");
            return "redirect:/auth/login";
        }
        String email = user.getEmail().trim();
        User dbUser = service.findUserByEmail(email);

        if (dbUser == null) {
            redirectAttributes.addFlashAttribute("formError", "User does not exist.");
            return "redirect:/auth/login";
        }

        if (!service.passwordMatches(user.getPassword().trim(), dbUser.getPassword())) {
            redirectAttributes.addFlashAttribute("formError", "Invalid Credentials");
            return "redirect:/auth/login";
        }

        if (!service.isAccountActive(dbUser)) {
            redirectAttributes.addFlashAttribute("formError", "Your account is currently suspended.");
            return "redirect:/auth/login";
        }

        session.setAttribute(SESSION_USER_EMAIL, dbUser.getEmail());
        session.setAttribute(SESSION_USER_ROLE, dbUser.getRole());
        redirectAttributes.addFlashAttribute("flashMessage", "Signed in successfully.");
        return "redirect:" + homePathForRole(dbUser.getRole());
    }

    @GetMapping("/signup")
    public String signup(Model model) {
        model.addAttribute("user", new User());
        return renderPage(model, "Create account", "auth/signup :: content");
    }

    @PostMapping("/signup")
    public String signupSubmit(
            @ModelAttribute("user") User user,
            @RequestParam(name = "profileImageFile", required = false) MultipartFile profileImageFile,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(user.getFirstName()) || isBlank(user.getLastName())
                || isBlank(user.getEmail())
                || isBlank(user.getPassword())
                || isBlank(user.getConfirmPassword())
                || isBlank(user.getRole())) {
            redirectAttributes.addFlashAttribute("formError", "Complete all required fields.");
            return "redirect:/auth/signup";
        }

        if (user.getEmail() != null && !user.getEmail().trim().isEmpty() && service.emailExists(user.getEmail())) {
            redirectAttributes.addFlashAttribute("formError", "User Email Exists.");
            return "redirect:/auth/signup";
        }

        if (!user.getPassword().equals(user.getConfirmPassword())) {
            redirectAttributes.addFlashAttribute("formError", "Password and confirmation do not match.");
            return "redirect:/auth/signup";
        }

        try {
            if (profileImageFile != null && !profileImageFile.isEmpty()) {
                user.setProfileImageUrl(profileImageStorageService.storeProfileImage(profileImageFile, user.getEmail()));
            }
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
            return "redirect:/auth/signup";
        }

        User createdUser = service.createUser(user);

        session.setAttribute(SESSION_USER_EMAIL, createdUser.getEmail());
        session.setAttribute(SESSION_USER_ROLE, createdUser.getRole());
        redirectAttributes.addFlashAttribute("flashMessage", "Account created successfully.");
        return "redirect:" + homePathForRole(user.getRole());
    }

    @GetMapping("/forgot")
    public String forgotPassword(Model model) {
        return renderPage(model, "Reset password", "auth/forgot :: content");
    }

    @PostMapping("/forgot")
    public String forgotPasswordSubmit(
            @RequestParam(defaultValue = "") String email,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(email)) {
            redirectAttributes.addFlashAttribute("formError", "Enter your account email.");
            return "redirect:/auth/forgot";
        }

        redirectAttributes.addFlashAttribute("flashMessage", "If your email exists, a reset link has been sent.");
        return "redirect:/auth/login";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        redirectAttributes.addFlashAttribute("flashMessage", "Signed out successfully.");
        return "redirect:/auth/login";
    }

    private String renderPage(Model model, String title, String content) {
        model.addAttribute("title", title);
        model.addAttribute("navbarType", PUBLIC_NAVBAR);
        model.addAttribute("content", content);
        return "layout";
    }

    private String homePathForRole(String role) {
        String normalizedRole = role == null ? "" : role.trim().toLowerCase();
        if ("mentee".equals(normalizedRole) || "seeker".equals(normalizedRole)) {
            return "/mentee/home";
        }
        return switch (normalizedRole) {
            case "mentor" -> "/mentor/home";
            case "admin" -> "/admin/home";
            default -> "/mentee/home";
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
