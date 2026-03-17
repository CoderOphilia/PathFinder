package com.pathfinder.mentor.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import com.pathfinder.auth.service.UserService;
import com.pathfinder.mentor.domain.MentorAvailability;
import com.pathfinder.mentor.domain.MentorInterviewCompany;
import com.pathfinder.mentor.domain.MentorInterviewCompanyId;
import com.pathfinder.mentor.domain.MentorProfile;
import com.pathfinder.mentor.domain.MentorSkill;
import com.pathfinder.mentor.domain.MentorSkillId;
import com.pathfinder.mentor.repo.MentorAvailabilityRepository;
import com.pathfinder.mentor.repo.MentorInterviewCompanyRepository;
import com.pathfinder.mentor.repo.MentorProfileRepository;
import com.pathfinder.mentor.repo.MentorSkillRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

@Service
public class MentorProfileService {

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorAvailabilityRepository mentorAvailabilityRepository;
    private final MentorInterviewCompanyRepository mentorInterviewCompanyRepository;
    private final MentorSkillRepository mentorSkillRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public MentorProfileService(
            MentorProfileRepository mentorProfileRepository,
            MentorAvailabilityRepository mentorAvailabilityRepository,
            MentorInterviewCompanyRepository mentorInterviewCompanyRepository,
            MentorSkillRepository mentorSkillRepository,
            UserService userService,
            UserRepository userRepository,
            EntityManager entityManager
    ) {
        this.mentorProfileRepository = mentorProfileRepository;
        this.mentorAvailabilityRepository = mentorAvailabilityRepository;
        this.mentorInterviewCompanyRepository = mentorInterviewCompanyRepository;
        this.mentorSkillRepository = mentorSkillRepository;
        this.userService = userService;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public MentorProfile saveProfile(
            String accountEmail,
            String fullName,
            String expertise,
            String hourlyRateCad,
            String currentTitle,
            String currentCompany,
            String interviewCompanies,
            String bio
    ) {
        User user = userService.findUserByEmail(accountEmail);
        if (user == null) {
            throw new IllegalArgumentException("No user exists for that email.");
        }
        if (!"mentor".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("That account is not registered as a mentor.");
        }

        User managedUser = userRepository.getReferenceById(user.getId());
        applyUserName(managedUser, fullName);
        MentorProfile profile = mentorProfileRepository.findById(user.getId()).orElse(null);

        if (profile == null) {
            MentorProfile newProfile = new MentorProfile();
            newProfile.setUserId(managedUser.getId());
            newProfile.setUser(managedUser);
            applyProfileValues(newProfile, expertise, hourlyRateCad, currentTitle, currentCompany, bio);
            entityManager.persist(newProfile);
            replaceSkills(newProfile, expertise);
            replaceInterviewCompanies(newProfile, interviewCompanies);
            return newProfile;
        }

        profile.setUserId(managedUser.getId());
        profile.setUser(managedUser);
        applyProfileValues(profile, expertise, hourlyRateCad, currentTitle, currentCompany, bio);
        replaceSkills(profile, expertise);
        replaceInterviewCompanies(profile, interviewCompanies);
        return profile;
    }

    @Transactional(readOnly = true)
    public MentorProfile findProfileByEmail(String accountEmail) {
        User user = findMentorUserByEmail(accountEmail);
        if (user == null) {
            return null;
        }
        return mentorProfileRepository.findById(user.getId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public User findMentorUserByEmail(String accountEmail) {
        User user = userService.findUserByEmail(accountEmail);
        if (user == null || !"mentor".equalsIgnoreCase(user.getRole())) {
            return null;
        }
        return user;
    }

    @Transactional(readOnly = true)
    public List<String> findSkillsByEmail(String accountEmail) {
        User user = findMentorUserByEmail(accountEmail);
        if (user == null) {
            return List.of();
        }
        return mentorSkillRepository.findByMentorProfileUserIdOrderBySkillNameAsc(user.getId()).stream()
                .map(MentorSkill::getSkillName)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> findInterviewCompaniesByEmail(String accountEmail) {
        User user = findMentorUserByEmail(accountEmail);
        if (user == null) {
            return List.of();
        }
        return mentorInterviewCompanyRepository.findByMentorProfileUserIdOrderByCompanyNameAsc(user.getId()).stream()
                .map(MentorInterviewCompany::getCompanyName)
                .toList();
    }

    @Transactional
    public void replaceAvailability(String accountEmail, List<AvailabilityInput> inputs) {
        User user = userService.findUserByEmail(accountEmail);
        if (user == null) {
            throw new IllegalArgumentException("No user exists for that email.");
        }
        if (!"mentor".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("That account is not registered as a mentor.");
        }

        MentorProfile profile = mentorProfileRepository.findById(user.getId()).orElse(null);
        if (profile == null) {
            throw new IllegalArgumentException("Save your mentor profile before setting availability.");
        }

        mentorAvailabilityRepository.deleteByMentorProfileUserId(user.getId());

        List<MentorAvailability> availabilityEntries = inputs.stream()
                .map(input -> {
                    MentorAvailability availability = new MentorAvailability();
                    availability.setMentorProfile(profile);
                    availability.setWeekday(input.weekday());
                    availability.setStartTime(LocalTime.parse(input.startTime()));
                    availability.setEndTime(LocalTime.parse(input.endTime()));
                    return availability;
                })
                .toList();

        mentorAvailabilityRepository.saveAll(availabilityEntries);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityInput> findAvailabilityByEmail(String accountEmail) {
        User user = findMentorUserByEmail(accountEmail);
        if (user == null) {
            return List.of();
        }
        return mentorAvailabilityRepository.findByMentorProfileUserIdOrderByWeekdayAscStartTimeAsc(user.getId()).stream()
                .map(item -> new AvailabilityInput(
                        item.getWeekday(),
                        item.getStartTime().toString(),
                        item.getEndTime().toString()
                ))
                .toList();
    }

    private void applyProfileValues(
            MentorProfile profile,
            String expertise,
            String hourlyRateCad,
            String currentTitle,
            String currentCompany,
            String bio
    ) {
        profile.setExpertise(normalizeText(expertise));
        profile.setHourlyRateCents(parseCadToCents(hourlyRateCad));
        profile.setCurrentTitle(normalizeText(currentTitle));
        profile.setCurrentCompany(normalizeText(currentCompany));
        profile.setBio(normalizeText(bio));
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

    private void replaceSkills(MentorProfile profile, String expertise) {
        Long mentorUserId = profile.getUserId();
        mentorSkillRepository.deleteByMentorProfileUserId(mentorUserId);

        List<String> skills = Arrays.stream(normalizeText(expertise).split(","))
                .map(this::normalizeText)
                .filter(skill -> !skill.isEmpty())
                .distinct()
                .limit(8)
                .toList();

        if (skills.isEmpty()) {
            return;
        }

        List<MentorSkill> mentorSkills = skills.stream()
                .map(skill -> {
                    MentorSkill mentorSkill = new MentorSkill();
                    mentorSkill.setId(new MentorSkillId(mentorUserId, skill));
                    mentorSkill.setMentorProfile(profile);
                    mentorSkill.setSkillName(skill);
                    return mentorSkill;
                })
                .toList();

        mentorSkillRepository.saveAll(mentorSkills);
    }

    private void replaceInterviewCompanies(MentorProfile profile, String interviewCompanies) {
        Long mentorUserId = profile.getUserId();
        mentorInterviewCompanyRepository.deleteByMentorProfileUserId(mentorUserId);

        List<String> companies = Arrays.stream(normalizeText(interviewCompanies).split(","))
                .map(this::normalizeText)
                .filter(company -> !company.isEmpty())
                .distinct()
                .limit(8)
                .toList();

        if (companies.isEmpty()) {
            return;
        }

        List<MentorInterviewCompany> entries = companies.stream()
                .map(company -> {
                    MentorInterviewCompany entry = new MentorInterviewCompany();
                    entry.setId(new MentorInterviewCompanyId(mentorUserId, company));
                    entry.setMentorProfile(profile);
                    entry.setCompanyName(company);
                    return entry;
                })
                .toList();

        mentorInterviewCompanyRepository.saveAll(entries);
    }

    private int parseCadToCents(String hourlyRateCad) {
        String normalized = normalizeText(hourlyRateCad);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Hourly rate is required.");
        }

        try {
            double dollars = Double.parseDouble(normalized);
            if (dollars < 0) {
                throw new IllegalArgumentException("Hourly rate must be zero or greater.");
            }
            return (int) Math.round(dollars * 100);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Hourly rate must be a valid number.");
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    public record AvailabilityInput(
            int weekday,
            String startTime,
            String endTime
    ) {
    }
}
