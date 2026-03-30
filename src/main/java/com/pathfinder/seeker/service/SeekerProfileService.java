package com.pathfinder.seeker.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import com.pathfinder.auth.service.UserService;
import com.pathfinder.mentor.domain.MentorProfile;
import com.pathfinder.seeker.domain.SeekerProfile;
import com.pathfinder.seeker.dto.SeekerProfileRequest;
import com.pathfinder.seeker.repo.SeekerRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class SeekerProfileService {
    private final SeekerRepository seekerRepository;
    private final UserRepository userRepository;
    private final UserService userService;



    public SeekerProfile saveSeekerProfile(String email, SeekerProfileRequest request) {

        User user = userService.findUserByEmail(email);

        if (user == null)
        {
            throw new IllegalArgumentException("No user exists for that email.");
        }

        if (!"mentee".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("That account is not registered as a mentor.");
        }

        User managedUser = userRepository.getReferenceById(user.getId());
        applyUserName(user, request.getFullName());


        SeekerProfile profile = seekerRepository.findById(user.getId())
                .orElseGet(() -> {
                    SeekerProfile p = new SeekerProfile();
                    p.setUser(user);
                    return p;
                });

        profile.setTargetRole(request.getTargetRole());
        profile.setExperienceLevel(request.getExperienceLevel());
        profile.setCurrentGoals(request.getCurrentGoals());
        return seekerRepository.save(profile);
    }


    public SeekerProfile findProfileByEmail(String accountEmail) {
        User user = findMenteeUserByEmail(accountEmail);
        if (user == null) {
            return null;
        }
        return seekerRepository.findById(user.getId()).orElse(null);
    }

    public User findMenteeUserByEmail(String accountEmail) {
        User user = userService.findUserByEmail(accountEmail);
        if (user == null || !"mentee".equalsIgnoreCase(user.getRole())) {
            return null;
        }
        return user;
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
