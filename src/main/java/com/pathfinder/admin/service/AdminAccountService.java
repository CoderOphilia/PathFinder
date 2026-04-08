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
            throw new IllegalStateException("Admin accounts cannot be suspended.");
        }
        if (!ACTIVE.equals(normalizeStatus(user.getAccountStatus()))) {
            throw new IllegalStateException("Only active accounts can be suspended.");
        }
        user.setAccountStatus(SUSPENDED);
    }

    public void reactivateUser(Long userId) {
        User user = requireUser(userId);
        if (isProtectedAccount(user)) {
            throw new IllegalStateException("Admin accounts cannot be changed here.");
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
                toRoleLabel(user.getRole()),
                normalizedStatus,
                ACTIVE.equals(normalizedStatus) && !protectedAccount,
                SUSPENDED.equals(normalizedStatus) && !protectedAccount
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

    private String toRoleLabel(String role) {
        String normalizedRole = normalizeText(role).toLowerCase(Locale.ROOT);
        if (normalizedRole.isEmpty()) {
            return "Unknown";
        }
        if ("mentee".equals(normalizedRole) || "seeker".equals(normalizedRole)) {
            return "Mentee";
        }
        return Character.toUpperCase(normalizedRole.charAt(0)) + normalizedRole.substring(1);
    }

    private String buildFullName(User user) {
        String fullName = (normalizeText(user.getFirstName()) + " " + normalizeText(user.getLastName())).trim();
        return fullName.isEmpty() ? user.getEmail() : fullName;
    }

    private String normalizeStatus(String status) {
        return normalizeText(status).isEmpty() ? ACTIVE : normalizeText(status).toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    public record ManagedUserView(
            Long userId,
            String fullName,
            String email,
            String role,
            String accountStatus,
            boolean canSuspend,
            boolean canReactivate
    ) {
    }
}
