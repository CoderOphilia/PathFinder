package com.pathfinder.auth.config;

import com.pathfinder.auth.web.AuthController;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final SessionRoleAuthenticationFilter sessionRoleAuthenticationFilter;

    public SecurityConfig(SessionRoleAuthenticationFilter sessionRoleAuthenticationFilter) {
        this.sessionRoleAuthenticationFilter = sessionRoleAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Existing Thymeleaf forms do not submit CSRF tokens yet, so keep this disabled until those forms are upgraded.
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
                .formLogin(form -> form.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .logout(logout -> logout.disable())
                .addFilterBefore(sessionRoleAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/",
                                "/auth/**",
                                "/error",
                                "/h2-console/**",
                                "/mentors/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/webjars/**"
                        ).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/mentor/**").hasRole("MENTOR")
                        .requestMatchers("/mentee/**", "/seeker/**").hasRole("MENTEE")
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendRedirect("/auth/login"))
                        .accessDeniedHandler((request, response, exception) ->
                                response.sendRedirect(homePathForRole(currentSessionRole(request.getSession(false)))))
                );

        return http.build();
    }

    private String currentSessionRole(HttpSession session) {
        if (session == null) {
            return "";
        }
        Object role = session.getAttribute(AuthController.SESSION_USER_ROLE);
        return role == null ? "" : role.toString();
    }

    private String homePathForRole(String role) {
        String normalizedRole = role == null ? "" : role.trim().toLowerCase();
        if ("mentee".equals(normalizedRole) || "seeker".equals(normalizedRole)) {
            return "/mentee/home";
        }
        return switch (normalizedRole) {
            case "mentor" -> "/mentor/home";
            case "admin" -> "/admin/home";
            default -> "/auth/login";
        };
    }
}
