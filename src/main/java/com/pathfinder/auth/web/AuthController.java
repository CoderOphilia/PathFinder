package com.pathfinder.auth.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/auth")
public class AuthController {

    private static final String PUBLIC_NAVBAR = "fragments/navbar :: navbar";

    @GetMapping("/login")
    public String login(Model model) {
        return renderPage(model, "Sign in", "auth/login :: content");
    }

    @PostMapping("/login")
    public String loginSubmit(
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "") String role,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(username) || isBlank(password) || isBlank(role)) {
            redirectAttributes.addFlashAttribute("formError", "Enter email, password, and role.");
            return "redirect:/auth/login";
        }

        redirectAttributes.addFlashAttribute("flashMessage", "Signed in successfully (demo mode).");
        return "redirect:" + homePathForRole(role);
    }

    @GetMapping("/signup")
    public String signup(Model model) {
        return renderPage(model, "Create account", "auth/signup :: content");
    }

    @PostMapping("/signup")
    public String signupSubmit(
            @RequestParam(defaultValue = "") String firstName,
            @RequestParam(defaultValue = "") String lastName,
            @RequestParam(defaultValue = "") String email,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "") String confirmPassword,
            @RequestParam(defaultValue = "") String role,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(firstName) || isBlank(lastName) || isBlank(email) || isBlank(password) || isBlank(confirmPassword) || isBlank(role)) {
            redirectAttributes.addFlashAttribute("formError", "Complete all required fields.");
            return "redirect:/auth/signup";
        }

        if (password.length() < 8) {
            redirectAttributes.addFlashAttribute("formError", "Password must be at least 8 characters.");
            return "redirect:/auth/signup";
        }

        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("formError", "Password and confirmation do not match.");
            return "redirect:/auth/signup";
        }

        redirectAttributes.addFlashAttribute("flashMessage", "Account created successfully (demo mode).");
        return "redirect:" + homePathForRole(role);
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

        redirectAttributes.addFlashAttribute("flashMessage", "If your email exists, a reset link has been sent (demo mode).");
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
        if ("mentee".equals(normalizedRole)) {
            return "/seeker/home";
        }
        return switch (normalizedRole) {
            case "mentor" -> "/mentor/home";
            case "admin" -> "/admin/home";
            default -> "/seeker/home";
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
