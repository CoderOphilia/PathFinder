package com.pathfinder.admin.service;

import com.pathfinder.admin.domain.AdminProfile;
import com.pathfinder.admin.repo.AdminProfileRepository;
import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional
public class AdminProfileService {

    private final UserRepository userRepository;
    private final AdminProfileRepository adminProfileRepository;

    public AdminProfileService(UserRepository userRepository, AdminProfileRepository adminProfileRepository) {
        this.userRepository = userRepository;
        this.adminProfileRepository = adminProfileRepository;
    }

    @Transactional(readOnly = true)
    public User findAdminUserByEmail(String email) {
        String normalizedEmail = normalizeText(email).toLowerCase(Locale.ROOT);
        if (normalizedEmail.isEmpty()) {
            return null;
        }
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            return null;
        }
        return user;
    }

    @Transactional(readOnly = true)
    public AdminProfile findProfileByEmail(String email) {
        User user = findAdminUserByEmail(email);
        if (user == null || user.getId() == null) {
            return null;
        }
        return adminProfileRepository.findById(user.getId()).orElse(null);
    }

    public AdminProfile saveProfile(String email, String team, String supportChannel, String notes) {
        User user = requireAdminUser(email);
        AdminProfile profile = adminProfileRepository.findById(user.getId())
                .orElseGet(() -> {
                    AdminProfile newProfile = new AdminProfile();
                    newProfile.setUserId(user.getId());
                    return newProfile;
                });
        profile.setTeam(normalizeText(team));
        profile.setSupportChannel(normalizeText(supportChannel));
        profile.setNotes(normalizeMultiline(notes));
        return adminProfileRepository.save(profile);
    }

    private User requireAdminUser(String email) {
        User user = findAdminUserByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("Admin account not found.");
        }
        if (user.getId() == null) {
            throw new IllegalArgumentException("Admin account is missing an id.");
        }
        return user;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeMultiline(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replace("\r\n", "\n");
    }
}
