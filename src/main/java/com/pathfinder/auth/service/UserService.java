package com.pathfinder.auth.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    private String normalizeRole(String role) {
        if (role == null) {
            return "seeker";
        }
        String normalizedRole = role.trim().toLowerCase();
        return switch (normalizedRole) {
            case "mentor", "admin" -> normalizedRole;
            default -> "seeker";
        };
    }
}
