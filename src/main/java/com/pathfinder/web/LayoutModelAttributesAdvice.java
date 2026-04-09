package com.pathfinder.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class LayoutModelAttributesAdvice {

    @ModelAttribute
    public void populateLayoutAttributes(HttpServletRequest request, Model model) {
        model.addAttribute("currentUrl", request.getRequestURI());
    }
}
