package com.pathfinder.auth.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class UserService {
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository repo) {
        this.userRepo = repo;
    }

    public User createUser(User user) {
        if (userRepo.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("A user with this email already exists");
        }

        user.setEmail(user.getEmail().trim().toLowerCase());
        user.setRole(normalizeRole(user.getRole()));
        user.setProfileImageUrl(normalizeProfileImageUrl(user.getProfileImageUrl()));
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setAccountStatus("ACTIVE");
        return userRepo.save(user);
    }

    public User findUserByEmail(String email) {
        if (email == null) {
            return null;
        }
        return userRepo.findByEmail(email.trim().toLowerCase()).orElse(null);
    }

    public boolean emailExists(String email) {
        if (email == null) {
            return false;
        }
        return userRepo.findByEmail(email.trim().toLowerCase()).isPresent();
    }

    public boolean passwordMatches(String rawPassword, String encodedPassword) {
        return rawPassword != null
                && encodedPassword != null
                && passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public boolean isAccountActive(User user) {
        if (user == null) {
            return false;
        }
        return "ACTIVE".equals(normalizeStatus(user.getAccountStatus()));
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "mentee";
        }
        String normalizedRole = role.trim().toLowerCase();
        return switch (normalizedRole) {
            case "seeker", "mentee" -> "mentee";
            case "mentor", "admin" -> normalizedRole;
            default -> "mentee";
        };
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return "ACTIVE";
        }
        String normalized = status.trim();
        if (normalized.isEmpty()) {
            return "ACTIVE";
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    public String normalizeProfileImageUrl(String profileImageUrl) {
        if (profileImageUrl == null) {
            return "";
        }
        return profileImageUrl.trim();
    }
}
