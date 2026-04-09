package com.pathfinder.admin.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import com.pathfinder.mentor.domain.MentorInterviewCompany;
import com.pathfinder.mentor.domain.MentorProfile;
import com.pathfinder.mentor.domain.MentorSkill;
import com.pathfinder.mentor.domain.VerificationStatus;
import com.pathfinder.mentor.repo.MentorInterviewCompanyRepository;
import com.pathfinder.mentor.repo.MentorSkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional
public class AdminReviewService {

    private final AdminReviewMentorProfileResolver mentorProfileResolver;
    private final MentorSkillRepository mentorSkillRepository;
    private final MentorInterviewCompanyRepository mentorInterviewCompanyRepository;
    private final UserRepository userRepository;

    public AdminReviewService(
            AdminReviewMentorProfileResolver mentorProfileResolver,
            MentorSkillRepository mentorSkillRepository,
            MentorInterviewCompanyRepository mentorInterviewCompanyRepository,
            UserRepository userRepository
    ) {
        this.mentorProfileResolver = mentorProfileResolver;
        this.mentorSkillRepository = mentorSkillRepository;
        this.mentorInterviewCompanyRepository = mentorInterviewCompanyRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<MentorReviewSummaryView> listReviewItems() {
        return userRepository.findAll().stream()
                .filter(user -> "mentor".equalsIgnoreCase(user.getRole()))
                .map(this::toReviewSummary)
                .filter(item -> item != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public MentorReviewDetailView findReviewItem(String mentorSlug) {
        String normalizedSlug = normalizeSlug(mentorSlug);
        if (normalizedSlug.isEmpty()) {
            return null;
        }

        return findMentorUserBySlug(normalizedSlug)
                .map(this::toReviewDetail)
                .orElse(null);
    }

    public void approveMentor(String mentorSlug, String adminNote) {
        MentorProfile profile = requireMentorProfile(mentorSlug);
        profile.setVerificationStatus(VerificationStatus.APPROVED);
        profile.setAdminNote(normalizeText(adminNote));
    }

    public void denyMentor(String mentorSlug, String adminNote) {
        String normalizedNote = normalizeText(adminNote);
        if (normalizedNote.isEmpty()) {
            throw new IllegalArgumentException("Enter a denial note before rejecting the mentor.");
        }
        MentorProfile profile = requireMentorProfile(mentorSlug);
        profile.setVerificationStatus(VerificationStatus.REJECTED);
        profile.setAdminNote(normalizedNote);
    }

    @Transactional(readOnly = true)
    public long pendingReviewCount() {
        return listReviewItems().stream()
                .filter(item -> "Pending review".equals(item.reviewStatus()))
                .count();
    }

    private MentorReviewSummaryView toReviewSummary(User mentorUser) {
        MentorProfile profile = mentorProfileResolver.findExistingOrDefault(mentorUser);
        MentorReviewFacts facts = buildFacts(mentorUser, profile);
        return new MentorReviewSummaryView(
                facts.slug(),
                facts.name(),
                facts.email(),
                facts.roleAtCompany(),
                facts.reviewStatus(),
                facts.statusClass(),
                facts.verificationSummary(),
                facts.completeChecks() + "/" + facts.totalChecks()
        );
    }

    private MentorProfile requireMentorProfile(String mentorSlug) {
        return findMentorUserBySlug(normalizeSlug(mentorSlug))
                .map(mentorProfileResolver::findExistingOrCreate)
                .orElseThrow(() -> new IllegalArgumentException("Mentor review item not found."));
    }

    private MentorReviewDetailView toReviewDetail(User mentorUser) {
        MentorProfile profile = mentorProfileResolver.findExistingOrDefault(mentorUser);
        MentorReviewFacts facts = buildFacts(mentorUser, profile);
        List<String> skills = findSkills(mentorUser.getId());
        List<String> interviewCompanies = findInterviewCompanies(mentorUser.getId());
        List<VerificationCheckView> verificationChecks = buildVerificationChecks(profile, skills, interviewCompanies);
        return new MentorReviewDetailView(
                facts.slug(),
                facts.name(),
                facts.email(),
                normalizeText(mentorUser.getProfileImageUrl()),
                facts.roleAtCompany(),
                normalizeText(profile.getExpertise()),
                formatCad(profile.getHourlyRateCents()),
                normalizeText(profile.getBio()),
                skills,
                interviewCompanies,
                toStatusLabel(profile.getVerificationStatus()),
                toStatusClass(profile.getVerificationStatus()),
                normalizeText(profile.getAdminNote()),
                facts.verificationSummary(),
                verificationChecks,
                profile.getSessionsCompleted() == null ? 0 : profile.getSessionsCompleted(),
                profile.isOffersFreeSession(),
                formatTrialSessionLabel(profile)
        );
    }

    private MentorReviewFacts buildFacts(User mentorUser, MentorProfile profile) {
        List<String> skills = findSkills(mentorUser.getId());
        List<String> interviewCompanies = findInterviewCompanies(mentorUser.getId());
        List<VerificationCheckView> verificationChecks = buildVerificationChecks(profile, skills, interviewCompanies);
        int completeChecks = (int) verificationChecks.stream().filter(VerificationCheckView::complete).count();
        return new MentorReviewFacts(
                normalizeSlug(buildFullName(mentorUser)),
                buildFullName(mentorUser),
                normalizeText(mentorUser.getEmail()),
                buildRoleAtCompany(profile),
                toStatusLabel(profile.getVerificationStatus()),
                toStatusClass(profile.getVerificationStatus()),
                buildVerificationSummary(verificationChecks),
                completeChecks,
                verificationChecks.size()
        );
    }

    private List<String> findSkills(Long mentorUserId) {
        return mentorSkillRepository.findByMentorProfileUserIdOrderBySkillNameAsc(mentorUserId).stream()
                .map(MentorSkill::getSkillName)
                .toList();
    }

    private List<String> findInterviewCompanies(Long mentorUserId) {
        return mentorInterviewCompanyRepository.findByMentorProfileUserIdOrderByCompanyNameAsc(mentorUserId).stream()
                .map(MentorInterviewCompany::getCompanyName)
                .toList();
    }

    private List<VerificationCheckView> buildVerificationChecks(
            MentorProfile profile,
            List<String> skills,
            List<String> interviewCompanies
    ) {
        List<VerificationCheckView> checks = new ArrayList<>();
        checks.add(new VerificationCheckView(
                "Current title",
                !normalizeText(profile.getCurrentTitle()).isEmpty(),
                normalizeText(profile.getCurrentTitle()).isEmpty() ? "Missing" : normalizeText(profile.getCurrentTitle())
        ));
        checks.add(new VerificationCheckView(
                "Current company",
                !normalizeText(profile.getCurrentCompany()).isEmpty(),
                normalizeText(profile.getCurrentCompany()).isEmpty() ? "Missing" : normalizeText(profile.getCurrentCompany())
        ));
        checks.add(new VerificationCheckView(
                "Expertise",
                !normalizeText(profile.getExpertise()).isEmpty(),
                normalizeText(profile.getExpertise()).isEmpty() ? "Missing" : "Added"
        ));
        checks.add(new VerificationCheckView(
                "Bio",
                !normalizeText(profile.getBio()).isEmpty(),
                normalizeText(profile.getBio()).isEmpty() ? "Missing" : "Added"
        ));
        checks.add(new VerificationCheckView(
                "Hourly rate",
                profile.getHourlyRateCents() != null && profile.getHourlyRateCents() > 0,
                profile.getHourlyRateCents() != null && profile.getHourlyRateCents() > 0 ? formatCad(profile.getHourlyRateCents()) : "Missing"
        ));
        checks.add(new VerificationCheckView(
                "Skills",
                !skills.isEmpty(),
                skills.isEmpty() ? "Missing" : skills.size() + " added"
        ));
        checks.add(new VerificationCheckView(
                "Interview companies",
                !interviewCompanies.isEmpty(),
                interviewCompanies.isEmpty() ? "Missing" : interviewCompanies.size() + " added"
        ));
        return checks;
    }

    private String buildVerificationSummary(List<VerificationCheckView> checks) {
        int total = checks.size();
        int complete = (int) checks.stream().filter(VerificationCheckView::complete).count();
        List<String> missing = checks.stream()
                .filter(check -> !check.complete())
                .map(VerificationCheckView::label)
                .toList();
        if (missing.isEmpty()) {
            return "Complete (" + complete + "/" + total + ")";
        }
        return complete + "/" + total + " complete • Missing " + String.join(", ", missing);
    }

    private Optional<User> findMentorUserBySlug(String mentorSlug) {
        if (mentorSlug.isEmpty()) {
            return Optional.empty();
        }
        return userRepository.findAll().stream()
                .filter(user -> "mentor".equalsIgnoreCase(user.getRole()))
                .filter(user -> normalizeSlug(buildFullName(user)).equals(mentorSlug))
                .findFirst();
    }

    private String toStatusLabel(VerificationStatus status) {
        if (status == null || status == VerificationStatus.PENDING) {
            return "Pending review";
        }
        if (status == VerificationStatus.APPROVED) {
            return "Approved";
        }
        return "Denied";
    }

    private String toStatusClass(VerificationStatus status) {
        if (status == null || status == VerificationStatus.PENDING) {
            return "statusBadge statusBadge--requested";
        }
        if (status == VerificationStatus.APPROVED) {
            return "statusBadge statusBadge--approved";
        }
        return "statusBadge statusBadge--declined";
    }

    private String buildFullName(User user) {
        return (normalizeText(user.getFirstName()) + " " + normalizeText(user.getLastName())).trim();
    }

    private String buildRoleAtCompany(MentorProfile profile) {
        String title = normalizeText(profile.getCurrentTitle());
        String company = normalizeText(profile.getCurrentCompany());
        if (title.isEmpty()) {
            return company;
        }
        if (company.isEmpty()) {
            return title;
        }
        return title + " @ " + company;
    }

    private String formatCad(Integer amountCents) {
        if (amountCents == null || amountCents <= 0) {
            return "";
        }
        return String.format(Locale.ROOT, "$%.2f", amountCents / 100.0);
    }

    private String formatTrialSessionLabel(MentorProfile profile) {
        if (!profile.isOffersFreeSession()
                || profile.getTrialSessionWeekday() == null
                || profile.getTrialSessionStartTime() == null
                || profile.getTrialSessionEndTime() == null) {
            return "";
        }
        return weekdayLabel(profile.getTrialSessionWeekday()) + " • "
                + formatTime(profile.getTrialSessionStartTime()) + " - " + formatTime(profile.getTrialSessionEndTime());
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

    private String formatTime(LocalTime time) {
        int hour = time.getHour();
        int convertedHour = hour % 12;
        if (convertedHour == 0) {
            convertedHour = 12;
        }
        return String.format(Locale.ROOT, "%d:%02d %s", convertedHour, time.getMinute(), hour >= 12 ? "PM" : "AM");
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

    public record MentorReviewSummaryView(
            String slug,
            String name,
            String email,
            String roleAtCompany,
            String reviewStatus,
            String statusClass,
            String verificationSummary,
            String verificationScore
    ) {
    }

    public record MentorReviewDetailView(
            String slug,
            String name,
            String email,
            String profileImageUrl,
            String roleAtCompany,
            String expertise,
            String hourlyRateLabel,
            String bio,
            List<String> skills,
            List<String> interviewCompanies,
            String reviewStatus,
            String statusClass,
            String adminNote,
            String verificationSummary,
            List<VerificationCheckView> verificationChecks,
            int sessionsCompleted,
            boolean offersFreeSession,
            String trialSessionLabel
    ) {
    }

    public record VerificationCheckView(
            String label,
            boolean complete,
            String detail
    ) {
    }

    private record MentorReviewFacts(
            String slug,
            String name,
            String email,
            String roleAtCompany,
            String reviewStatus,
            String statusClass,
            String verificationSummary,
            int completeChecks,
            int totalChecks
    ) {
    }
}
