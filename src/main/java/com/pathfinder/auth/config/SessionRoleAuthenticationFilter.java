package com.pathfinder.auth.config;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.service.UserService;
import com.pathfinder.auth.web.AuthController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Component
public class SessionRoleAuthenticationFilter extends OncePerRequestFilter {

    private final ObjectProvider<UserService> userServiceProvider;

    public SessionRoleAuthenticationFilter(ObjectProvider<UserService> userServiceProvider) {
        this.userServiceProvider = userServiceProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                User authenticatedUser = resolveAuthenticatedUser(session);
                if (authenticatedUser != null) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            authenticatedUser.getEmail(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + authorityRole(authenticatedUser.getRole())))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private User resolveAuthenticatedUser(HttpSession session) {
        Object sessionEmail = session.getAttribute(AuthController.SESSION_USER_EMAIL);
        Object sessionRole = session.getAttribute(AuthController.SESSION_USER_ROLE);
        if (sessionEmail == null || sessionRole == null) {
            return null;
        }

        String email = normalizeText(sessionEmail.toString()).toLowerCase(Locale.ROOT);
        if (email.isEmpty()) {
            return null;
        }

        UserService userService = userServiceProvider.getIfAvailable();
        if (userService == null) {
            return sessionBackedUser(email, sessionRole.toString());
        }

        User user = userService.findUserByEmail(email);
        if (user == null || !userService.isAccountActive(user)) {
            session.invalidate();
            SecurityContextHolder.clearContext();
            return null;
        }
        session.setAttribute(AuthController.SESSION_USER_ROLE, user.getRole());
        session.setAttribute(AuthController.SESSION_USER_EMAIL, user.getEmail());
        return user;
    }

    private User sessionBackedUser(String email, String role) {
        User user = new User();
        user.setEmail(email);
        user.setRole(role);
        user.setAccountStatus("ACTIVE");
        return user;
    }

    private String authorityRole(String role) {
        String normalizedRole = normalizeText(role).toLowerCase(Locale.ROOT);
        return switch (normalizedRole) {
            case "seeker", "mentee" -> "MENTEE";
            case "mentor" -> "MENTOR";
            case "admin" -> "ADMIN";
            default -> "MENTEE";
        };
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
