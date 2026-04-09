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
import com.pathfinder.mentor.domain.VerificationStatus;
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
import java.util.Locale;
import java.util.Objects;

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
            String profileImageUrl,
            String expertise,
            String hourlyRateCad,
            boolean offersFreeSession,
            String trialSessionWeekday,
            String trialSessionStartTime,
            String trialSessionEndTime,
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
        applyUserDetails(managedUser, fullName, profileImageUrl);
        MentorProfile profile = mentorProfileRepository.findById(user.getId()).orElse(null);

        if (profile == null) {
            MentorProfile newProfile = new MentorProfile();
            newProfile.setUserId(managedUser.getId());
            newProfile.setUser(managedUser);
            newProfile.setVerificationStatus(VerificationStatus.PENDING);
            newProfile.setAdminNote("");
            applyProfileValues(
                    newProfile,
                    expertise,
                    hourlyRateCad,
                    offersFreeSession,
                    trialSessionWeekday,
                    trialSessionStartTime,
                    trialSessionEndTime,
                    currentTitle,
                    currentCompany,
                    bio
            );
            entityManager.persist(newProfile);
            replaceSkills(newProfile, expertise);
            replaceInterviewCompanies(newProfile, interviewCompanies);
            return newProfile;
        }

        profile.setUserId(managedUser.getId());
        profile.setUser(managedUser);
        applyProfileValues(
                profile,
                expertise,
                hourlyRateCad,
                offersFreeSession,
                trialSessionWeekday,
                trialSessionStartTime,
                trialSessionEndTime,
                currentTitle,
                currentCompany,
                bio
        );
        replaceSkills(profile, expertise);
        replaceInterviewCompanies(profile, interviewCompanies);
        return profile;
    }

    @Transactional
    public MentorProfile saveProfile(
            String accountEmail,
            String fullName,
            String expertise,
            String hourlyRateCad,
            boolean offersFreeSession,
            String trialSessionWeekday,
            String trialSessionStartTime,
            String trialSessionEndTime,
            String currentTitle,
            String currentCompany,
            String interviewCompanies,
            String bio
    ) {
        return saveProfile(
                accountEmail,
                fullName,
                "",
                expertise,
                hourlyRateCad,
                offersFreeSession,
                trialSessionWeekday,
                trialSessionStartTime,
                trialSessionEndTime,
                currentTitle,
                currentCompany,
                interviewCompanies,
                bio
        );
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

    @Transactional(readOnly = true)
    public PublicMentorProfile findPublicProfileBySlug(String mentorSlug) {
        String normalizedSlug = normalizeSlug(mentorSlug);
        if (normalizedSlug.isEmpty()) {
            return null;
        }

        return userRepository.findAll().stream()
                .filter(user -> "mentor".equalsIgnoreCase(user.getRole()))
                .map(user -> toPublicMentorProfile(user, findApprovedPublicProfile(user.getId())))
                .filter(Objects::nonNull)
                .filter(profile -> profile.slug().equals(normalizedSlug))
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<PublicMentorProfile> listPublicMentors() {
        return userRepository.findAll().stream()
                .filter(user -> "mentor".equalsIgnoreCase(user.getRole()))
                .map(user -> toPublicMentorProfile(user, findApprovedPublicProfile(user.getId())))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public String findMentorEmailByName(String mentorName) {
        String normalizedName = normalizeText(mentorName);
        if (normalizedName.isEmpty()) {
            return "";
        }

        return userRepository.findAll().stream()
                .filter(user -> "mentor".equalsIgnoreCase(user.getRole()))
                .filter(user -> findApprovedPublicProfile(user.getId()) != null)
                .filter(user -> buildFullName(user.getFirstName(), user.getLastName()).equalsIgnoreCase(normalizedName))
                .map(User::getEmail)
                .findFirst()
                .orElse("");
    }

    @Transactional(readOnly = true)
    public List<AvailabilityInput> findAvailabilityByMentorName(String mentorName) {
        String mentorEmail = findMentorEmailByName(mentorName);
        if (mentorEmail.isEmpty()) {
            return List.of();
        }
        return findAvailabilityByEmail(mentorEmail);
    }

    @Transactional(readOnly = true)
    public TrialSessionAvailability findTrialAvailabilityByMentorName(String mentorName) {
        String mentorEmail = findMentorEmailByName(mentorName);
        if (mentorEmail.isEmpty()) {
            return null;
        }
        MentorProfile profile = findProfileByEmail(mentorEmail);
        return toTrialAvailability(profile);
    }

    private PublicMentorProfile toPublicMentorProfile(User user, MentorProfile profile) {
        if (user == null || profile == null) {
            return null;
        }

        String name = buildFullName(user.getFirstName(), user.getLastName());
        String currentTitle = normalizeText(profile.getCurrentTitle());
        String currentCompany = normalizeText(profile.getCurrentCompany());
        String roleAtCompany = buildRoleAtCompany(currentTitle, currentCompany);
        List<String> skills = mentorSkillRepository.findByMentorProfileUserIdOrderBySkillNameAsc(user.getId()).stream()
                .map(MentorSkill::getSkillName)
                .toList();
        List<String> interviewCompanies = mentorInterviewCompanyRepository.findByMentorProfileUserIdOrderByCompanyNameAsc(user.getId()).stream()
                .map(MentorInterviewCompany::getCompanyName)
                .toList();

        return new PublicMentorProfile(
                normalizeSlug(name),
                name,
                user.getProfileImageUrl(),
                roleAtCompany,
                formatCad(profile.getHourlyRateCents()),
                profile.isOffersFreeSession(),
                formatTrialSessionLabel(toTrialAvailability(profile)),
                normalizeText(profile.getBio()),
                skills,
                interviewCompanies,
                profile.getSessionsCompleted() == null ? 0 : profile.getSessionsCompleted()
        );
    }

    private boolean isPubliclyVisible(MentorProfile profile) {
        return profile != null && profile.getVerificationStatus() == VerificationStatus.APPROVED;
    }

    private MentorProfile findApprovedPublicProfile(Long userId) {
        MentorProfile profile = mentorProfileRepository.findById(userId).orElse(null);
        return isPubliclyVisible(profile) ? profile : null;
    }

    private void applyProfileValues(
            MentorProfile profile,
            String expertise,
            String hourlyRateCad,
            boolean offersFreeSession,
            String trialSessionWeekday,
            String trialSessionStartTime,
            String trialSessionEndTime,
            String currentTitle,
            String currentCompany,
            String bio
    ) {
        profile.setExpertise(normalizeText(expertise));
        profile.setHourlyRateCents(parseCadToCents(hourlyRateCad));
        profile.setOffersFreeSession(offersFreeSession);
        applyTrialSessionValues(profile, offersFreeSession, trialSessionWeekday, trialSessionStartTime, trialSessionEndTime);
        profile.setCurrentTitle(normalizeText(currentTitle));
        profile.setCurrentCompany(normalizeText(currentCompany));
        profile.setBio(normalizeText(bio));
    }

    private void applyTrialSessionValues(
            MentorProfile profile,
            boolean offersFreeSession,
            String trialSessionWeekday,
            String trialSessionStartTime,
            String trialSessionEndTime
    ) {
        if (!offersFreeSession) {
            profile.setTrialSessionWeekday(null);
            profile.setTrialSessionStartTime(null);
            profile.setTrialSessionEndTime(null);
            return;
        }

        String normalizedWeekday = normalizeText(trialSessionWeekday);
        String normalizedStart = normalizeText(trialSessionStartTime);
        String normalizedEnd = normalizeText(trialSessionEndTime);
        if (normalizedWeekday.isEmpty() || normalizedStart.isEmpty() || normalizedEnd.isEmpty()) {
            throw new IllegalArgumentException("Choose a weekday, start time, and end time for trial sessions.");
        }

        int weekday = parseWeekdayKey(normalizedWeekday);
        LocalTime startTime = parseTime(normalizedStart, "Trial session start time is invalid.");
        LocalTime endTime = parseTime(normalizedEnd, "Trial session end time is invalid.");
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Trial session end time must be after the start time.");
        }

        profile.setTrialSessionWeekday(weekday);
        profile.setTrialSessionStartTime(startTime);
        profile.setTrialSessionEndTime(endTime);
    }

    private void applyUserDetails(User user, String fullName, String profileImageUrl) {
        String normalized = normalizeText(fullName);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Name is required.");
        }

        int firstSpace = normalized.indexOf(' ');
        if (firstSpace < 0) {
            user.setFirstName(normalized);
            user.setLastName("");
        } else {
            user.setFirstName(normalized.substring(0, firstSpace));
            user.setLastName(normalized.substring(firstSpace + 1).trim());
        }
        user.setProfileImageUrl(userService.normalizeProfileImageUrl(profileImageUrl));
    }

    private String buildFullName(String firstName, String lastName) {
        return (normalizeText(firstName) + " " + normalizeText(lastName)).trim();
    }

    private String buildRoleAtCompany(String title, String company) {
        if (title.isEmpty() && company.isEmpty()) {
            return "";
        }
        if (title.isEmpty()) {
            return company;
        }
        if (company.isEmpty()) {
            return title;
        }
        return title + " @ " + company;
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

    private LocalTime parseTime(String value, String errorMessage) {
        try {
            return LocalTime.parse(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private int parseWeekdayKey(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "sun" -> 1;
            case "mon" -> 2;
            case "tue" -> 3;
            case "wed" -> 4;
            case "thu" -> 5;
            case "fri" -> 6;
            case "sat" -> 7;
            default -> throw new IllegalArgumentException("Choose a valid weekday for trial sessions.");
        };
    }

    private TrialSessionAvailability toTrialAvailability(MentorProfile profile) {
        if (profile == null
                || !profile.isOffersFreeSession()
                || profile.getTrialSessionWeekday() == null
                || profile.getTrialSessionStartTime() == null
                || profile.getTrialSessionEndTime() == null) {
            return null;
        }
        return new TrialSessionAvailability(
                profile.getTrialSessionWeekday(),
                profile.getTrialSessionStartTime().toString(),
                profile.getTrialSessionEndTime().toString()
        );
    }

    private String formatTrialSessionLabel(TrialSessionAvailability availability) {
        if (availability == null) {
            return "";
        }
        return weekdayLabel(availability.weekday()) + " • "
                + formatTime(availability.startTime()) + " - " + formatTime(availability.endTime());
    }

    private String weekdayLabel(int weekday) {
        return switch (weekday) {
            case 1 -> "Sunday";
            case 2 -> "Monday";
            case 3 -> "Tuesday";
            case 4 -> "Wednesday";
            case 5 -> "Thursday";
            case 6 -> "Friday";
            case 7 -> "Saturday";
            default -> "Day";
        };
    }

    private String formatTime(String value) {
        LocalTime time = LocalTime.parse(value);
        int hour = time.getHour();
        int convertedHour = hour % 12;
        if (convertedHour == 0) {
            convertedHour = 12;
        }
        return String.format(Locale.ROOT, "%d:%02d %s", convertedHour, time.getMinute(), hour >= 12 ? "PM" : "AM");
    }

    private String formatCad(Integer amountCents) {
        if (amountCents == null) {
            return "";
        }
        if (amountCents % 100 == 0) {
            return "$" + (amountCents / 100) + "/hr";
        }
        return String.format(Locale.ROOT, "$%.2f/hr", amountCents / 100.0);
    }

    private String normalizeSlug(String value) {
        return normalizeText(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
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

    public record PublicMentorProfile(
            String slug,
            String name,
            String profileImageUrl,
            String roleAtCompany,
            String rate,
            boolean offersFreeSession,
            String trialSessionLabel,
            String tagline,
            List<String> skills,
            List<String> interviewCompanies,
            int sessionsCompleted
    ) {
    }

    public record TrialSessionAvailability(
            int weekday,
            String startTime,
            String endTime
    ) {
    }




}
