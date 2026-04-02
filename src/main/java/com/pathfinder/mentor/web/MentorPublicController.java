package com.pathfinder.mentor.web;

import com.pathfinder.mentor.service.MentorProfileService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MentorPublicController {

    private static final String PUBLIC_NAVBAR = "fragments/navbar :: navbar";

    private final MentorProfileService mentorProfileService;

    public MentorPublicController(MentorProfileService mentorProfileService) {
        this.mentorProfileService = mentorProfileService;
    }

    @GetMapping("/mentors/{mentorSlug}")
    public String publicProfile(
            @PathVariable String mentorSlug,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        MentorProfileService.PublicMentorProfile mentor = mentorProfileService.findPublicProfileBySlug(mentorSlug);
        if (mentor == null) {
            redirectAttributes.addFlashAttribute("formError", "Mentor profile not found.");
            return "redirect:/mentee/mentors";
        }

        model.addAttribute("mentor", mentor);
        model.addAttribute("title", mentor.name() + " | Mentor profile");
        model.addAttribute("navbarType", PUBLIC_NAVBAR);
        model.addAttribute("content", "mentor/public_profile :: content");
        return "layout";
    }
}
