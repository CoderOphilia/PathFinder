package com.pathfinder.mentee.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import com.pathfinder.auth.service.UserService;
import com.pathfinder.mentee.domain.MenteeProfile;
import com.pathfinder.mentee.dto.MenteeProfileRequest;
import com.pathfinder.mentee.repo.MenteeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class MenteeProfileService {
    private final MenteeRepository menteeRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public MenteeProfile saveMenteeProfile(String email, MenteeProfileRequest request) {
        User user = userService.findUserByEmail(email);

        if (user == null) {
            throw new IllegalArgumentException("No user exists for that email.");
        }

        if (!isMenteeRole(user.getRole())) {
            throw new IllegalArgumentException("That account is not registered as a mentee.");
        }

        User managedUser = userRepository.getReferenceById(user.getId());
        applyUserName(managedUser, request.getFullName());

        MenteeProfile profile = menteeRepository.findById(user.getId())
                .orElseGet(() -> {
                    MenteeProfile p = new MenteeProfile();
                    p.setUser(managedUser);
                    return p;
                });

        profile.setTargetRole(request.getTargetRole());
        profile.setExperienceLevel(request.getExperienceLevel());
        profile.setCurrentGoals(request.getCurrentGoals());
        profile.setTimezone(request.getTimezone());
        return menteeRepository.save(profile);
    }

    public MenteeProfile findProfileByEmail(String accountEmail) {
        User user = findMenteeUserByEmail(accountEmail);
        if (user == null) {
            return null;
        }
        return menteeRepository.findById(user.getId()).orElse(null);
    }

    public User findMenteeUserByEmail(String accountEmail) {
        User user = userService.findUserByEmail(accountEmail);
        if (user == null || !isMenteeRole(user.getRole())) {
            return null;
        }
        return user;
    }

    private boolean isMenteeRole(String role) {
        return "mentee".equalsIgnoreCase(role) || "seeker".equalsIgnoreCase(role);
    }

    private void applyUserName(User user, String fullName) {
        String normalized = normalizeText(fullName);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Name is required.");
        }

        int firstSpace = normalized.indexOf(' ');
        if (firstSpace < 0) {
            user.setFirstName(normalized);
            user.setLastName("");
            return;
        }

        user.setFirstName(normalized.substring(0, firstSpace));
        user.setLastName(normalized.substring(firstSpace + 1).trim());
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
