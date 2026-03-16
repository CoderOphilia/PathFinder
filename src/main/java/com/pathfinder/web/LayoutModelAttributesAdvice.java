package com.pathfinder.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class LayoutModelAttributesAdvice {

    private final Environment environment;
    private final boolean developerNavbarEnabled;
    private final boolean developerNavbarAllowedInProduction;

    public LayoutModelAttributesAdvice(
            Environment environment,
            @Value("${pathfinder.dev-navbar.enabled:true}") boolean developerNavbarEnabled,
            @Value("${pathfinder.dev-navbar.allow-in-production:false}") boolean developerNavbarAllowedInProduction
    ) {
        this.environment = environment;
        this.developerNavbarEnabled = developerNavbarEnabled;
        this.developerNavbarAllowedInProduction = developerNavbarAllowedInProduction;
    }

    @ModelAttribute
    public void populateLayoutAttributes(HttpServletRequest request, Model model) {
        boolean hasDevelopmentProfile = environment.acceptsProfiles(Profiles.of("dev", "local"));
        boolean hasProductionProfile = environment.acceptsProfiles(Profiles.of("prod", "production"));
        boolean devMode = developerNavbarEnabled
                && (hasDevelopmentProfile || !hasProductionProfile || developerNavbarAllowedInProduction);
        model.addAttribute("devMode", devMode);
        model.addAttribute("currentUrl", request.getRequestURI());
    }
}
