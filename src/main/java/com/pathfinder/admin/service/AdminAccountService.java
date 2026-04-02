package com.pathfinder.admin.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class AdminAccountService {

    private static final String ACTIVE = "ACTIVE";
    private static final String SUSPENDED = "SUSPENDED";

    private final UserRepository userRepository;

    public AdminAccountService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ManagedUserView> listUsers() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "role", "email")).stream()
                .map(this::toView)
                .toList();
    }

    public void suspendUser(Long userId) {
        User user = requireUser(userId);
        if (isProtectedAccount(user)) {
            throw new IllegalStateException("Admin accounts cannot be suspended from this screen.");
        }
        if (!ACTIVE.equals(normalizeStatus(user.getAccountStatus()))) {
            throw new IllegalStateException("Only active accounts can be suspended.");
        }
        user.setAccountStatus(SUSPENDED);
    }

    public void reactivateUser(Long userId) {
        User user = requireUser(userId);
        if (isProtectedAccount(user)) {
            throw new IllegalStateException("Admin accounts cannot be changed from this screen.");
        }
        if (!SUSPENDED.equals(normalizeStatus(user.getAccountStatus()))) {
            throw new IllegalStateException("Only suspended accounts can be reactivated.");
        }
        user.setAccountStatus(ACTIVE);
    }

    @Transactional(readOnly = true)
    public long totalUserCount() {
        return userRepository.count();
    }

    private ManagedUserView toView(User user) {
        String normalizedStatus = normalizeStatus(user.getAccountStatus());
        boolean protectedAccount = isProtectedAccount(user);
        return new ManagedUserView(
                user.getId(),
                buildFullName(user),
                user.getEmail(),
                normalizeRole(user.getRole()),
                normalizedStatus,
                ACTIVE.equals(normalizedStatus) && !protectedAccount,
                SUSPENDED.equals(normalizedStatus) && !protectedAccount,
                protectedAccount
        );
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User not found.");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
    }

    private boolean isProtectedAccount(User user) {
        return "admin".equalsIgnoreCase(user.getRole());
    }

    private String normalizeStatus(String status) {
        return status == null ? ACTIVE : status.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "Unknown";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if ("seeker".equals(normalized) || "mentee".equals(normalized)) {
            return "Mentee";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String buildFullName(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? user.getEmail() : fullName;
    }

    public record ManagedUserView(
            Long userId,
            String fullName,
            String email,
            String role,
            String accountStatus,
            boolean canSuspend,
            boolean canReactivate,
            boolean protectedAccount
    ) {
    }
}
