package com.pathfinder.landing.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LandingController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("navbarType", "fragments/navbar :: navbar");
        model.addAttribute("content", "landing/index :: content");
        return "layout";
    }
}
