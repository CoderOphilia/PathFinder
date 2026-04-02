package com.pathfinder.mentee.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import com.pathfinder.auth.service.UserService;
import com.pathfinder.mentee.domain.MenteeExperienceLevel;
import com.pathfinder.mentee.domain.MenteeProfile;
import com.pathfinder.mentee.dto.MentorDirectoryItemView;
import com.pathfinder.mentee.repo.MenteeRepository;
import com.pathfinder.mentor.domain.MentorProfile;
import com.pathfinder.mentor.repo.MentorProfileRepository;
import com.pathfinder.mentor.service.MentorProfileService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class MenteeProfileService {
    private final MenteeRepository menteeRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final MentorProfileService mentorProfileService;
    private final MentorProfileRepository mentorProfileRepository;


    private List<MentorDirectoryItemView> cachedMentors = null;
    private long cacheTime = 0;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;


    // saving Mentee profile
    public MenteeProfile saveMenteeProfile(Long userId,
                                           String targetRole,
                                           String experienceLevel,
                                           String timeZone,
                                           String currentGoals) {
        ;



        MenteeProfile profile = menteeRepository.findById(userId)
                .orElseGet(() -> {
                    MenteeProfile p = new MenteeProfile();
                    p.setUser(userRepository.getReferenceById(userId));
                    return p;
                });

        profile.setTargetRole(normalizeText(targetRole));
        profile.setExperienceLevel(MenteeExperienceLevel.valueOf(normalizeText(experienceLevel)));
        profile.setCurrentGoals(normalizeText(currentGoals));
        profile.setTimezone(normalizeText(timeZone));
        return menteeRepository.save(profile);
    }

    public List<MentorDirectoryItemView> getAllMentors() {
        if (cachedMentors == null || System.currentTimeMillis() - cacheTime > CACHE_TTL_MS) {
            cachedMentors = mentorProfileService.listPublicMentors().stream()
                    .map(profile -> new MentorDirectoryItemView(
                            profile.slug(),
                            profile.name(),
                            profile.rate(),
                            profile.roleAtCompany(),
                            profile.tagline(),
                            profile.skills(),
                            profile.interviewCompanies(),
                            profile.sessionsCompleted()
                    ))
                    .sorted(Comparator.comparing(MentorDirectoryItemView::name))
                    .toList();
            cacheTime = System.currentTimeMillis();
        }
        return cachedMentors;

    }


    public List<MentorDirectoryItemView> searchFilterMentors(String searchTerm,String interviewCompany) {

        String query = searchTerm == null ? "" : searchTerm.trim().toLowerCase(Locale.ROOT);
        String selectedCompany = safeTrim(interviewCompany);
        if (query.isEmpty() && selectedCompany.isEmpty()) return getAllMentors();


        return getAllMentors().stream()
                .filter(mentor -> matchesQuery(mentor, query))
                .filter(mentor -> selectedCompany.isEmpty() || mentor.interviewCompanies().stream()
                        .anyMatch(company -> company.equalsIgnoreCase(selectedCompany)))
                .toList();

    }

    public  List<String> getCompaniesList(String interviewCompany) {
        List<MentorDirectoryItemView> mentors = getAllMentors();
        return mentors.stream()
                .flatMap(mentor -> mentor.interviewCompanies().stream())
                .distinct()
                .sorted()
                .toList();


    }









    // helper functions


    private boolean matchesQuery(MentorDirectoryItemView mentor, String query) {
        return containsIgnoreCase(mentor.name(), query)
                || containsIgnoreCase(mentor.roleAtCompany(), query)
                || containsIgnoreCase(mentor.tagline(), query)
//                || containsIgnoreCase(mentor.industry(), query)
                || mentor.skills().stream().anyMatch(skill -> containsIgnoreCase(skill, query))
                || mentor.interviewCompanies().stream().anyMatch(company -> containsIgnoreCase(company, query));
    }
    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    public Optional<User> findUserbyEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public MenteeProfile findProfileByEmail(String accountEmail) {
        User user = findMenteeUserByEmail(accountEmail);
        if (user == null) {
            return null;
        }
        return menteeRepository.findById(user.getId()).orElse(null);
    }
    public Optional<MenteeProfile> findProfileByUser(User user) {
        return menteeRepository.findByUser(user);
    }

    public User findMenteeUserByEmail(String accountEmail) {
        User user = userService.findUserByEmail(accountEmail);
        if (user == null || !isMenteeRole(user.getRole())) {
            return null;
        }
        return user;
    }
    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }


    private boolean isMenteeRole(String role) {
        return "mentee".equalsIgnoreCase(role) || "seeker".equalsIgnoreCase(role);
    }



    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
