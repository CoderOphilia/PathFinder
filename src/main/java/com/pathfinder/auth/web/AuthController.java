package com.pathfinder.auth.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/auth")
public class AuthController {

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("title", "Login");
        model.addAttribute("navbarType", "fragments/navbar :: navbar");
        model.addAttribute("content", "auth/login :: content");
        return "layout";
    }

    @GetMapping("/signup")
    public String signup(Model model) {
        model.addAttribute("title", "Signup");
        model.addAttribute("navbarType", "fragments/navbar :: navbar");
        model.addAttribute("content", "auth/signup :: content");
        return "layout";
    }

    @GetMapping("/forgot")
    public String forgotPassword(Model model) {
        model.addAttribute("title", "Reset password");
        model.addAttribute("navbarType", "fragments/navbar :: navbar");
        model.addAttribute("content", "auth/forgot :: content");
        return "layout";
    }
}
