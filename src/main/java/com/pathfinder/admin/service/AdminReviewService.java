package com.pathfinder.admin.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import com.pathfinder.mentor.domain.MentorInterviewCompany;
import com.pathfinder.mentor.domain.MentorProfile;
import com.pathfinder.mentor.domain.MentorSkill;
import com.pathfinder.mentor.domain.VerificationStatus;
import com.pathfinder.mentor.repo.MentorInterviewCompanyRepository;
import com.pathfinder.mentor.repo.MentorProfileRepository;
import com.pathfinder.mentor.repo.MentorSkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class AdminReviewService {

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorSkillRepository mentorSkillRepository;
    private final MentorInterviewCompanyRepository mentorInterviewCompanyRepository;
    private final UserRepository userRepository;

    public AdminReviewService(
            MentorProfileRepository mentorProfileRepository,
            MentorSkillRepository mentorSkillRepository,
            MentorInterviewCompanyRepository mentorInterviewCompanyRepository,
            UserRepository userRepository
    ) {
        this.mentorProfileRepository = mentorProfileRepository;
        this.mentorSkillRepository = mentorSkillRepository;
        this.mentorInterviewCompanyRepository = mentorInterviewCompanyRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<MentorReviewItemView> listReviewItems() {
        // Admin reads every mentor user, then builds a simple row for the review page.
        return userRepository.findAll().stream()
                .filter(user -> "mentor".equalsIgnoreCase(user.getRole()))
                .map(this::toReviewItem)
                .filter(item -> item != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public MentorReviewItemView findReviewItem(String mentorSlug) {
        String normalizedSlug = normalizeSlug(mentorSlug);
        if (normalizedSlug.isEmpty()) {
            return null;
        }

        return listReviewItems().stream()
                .filter(item -> item.slug().equals(normalizedSlug))
                .findFirst()
                .orElse(null);
    }

    public void approveMentor(String mentorSlug, String adminNote) {
        // Save the admin decision directly on the mentor profile.
        MentorProfile profile = requireMentorProfile(mentorSlug);
        profile.setVerificationStatus(VerificationStatus.APPROVED);
        profile.setAdminNote(normalizeText(adminNote));
    }

    public void denyMentor(String mentorSlug, String adminNote) {
        // Save the admin decision directly on the mentor profile.
        MentorProfile profile = requireMentorProfile(mentorSlug);
        // The existing enum already has REJECTED, so we use it for the denied state.
        profile.setVerificationStatus(VerificationStatus.REJECTED);
        profile.setAdminNote(normalizeText(adminNote));
    }

    @Transactional(readOnly = true)
    public long pendingReviewCount() {
        return listReviewItems().stream()
                .filter(item -> "Pending review".equals(item.reviewStatus()))
                .count();
    }

    private MentorReviewItemView toReviewItem(User mentorUser) {
        // Pull the saved mentor profile and turn it into a small view object for the template.
        MentorProfile profile = mentorProfileRepository.findById(mentorUser.getId()).orElse(null);
        if (profile == null) {
            return null;
        }

        List<String> skills = mentorSkillRepository.findByMentorProfileUserIdOrderBySkillNameAsc(mentorUser.getId()).stream()
                .map(MentorSkill::getSkillName)
                .toList();
        List<String> interviewCompanies = mentorInterviewCompanyRepository.findByMentorProfileUserIdOrderByCompanyNameAsc(mentorUser.getId()).stream()
                .map(MentorInterviewCompany::getCompanyName)
                .toList();

        return new MentorReviewItemView(
                normalizeSlug(buildFullName(mentorUser)),
                buildFullName(mentorUser),
                buildRoleAtCompany(profile),
                normalizeText(profile.getBio()),
                skills,
                interviewCompanies,
                toStatusLabel(profile.getVerificationStatus()),
                toStatusClass(profile.getVerificationStatus()),
                normalizeText(profile.getAdminNote())
        );
    }

    private MentorProfile requireMentorProfile(String mentorSlug) {
        // Find the real mentor profile that matches the selected slug before updating it.
        MentorReviewItemView item = findReviewItem(mentorSlug);
        if (item == null) {
            throw new IllegalArgumentException("Mentor review item not found.");
        }

        return userRepository.findAll().stream()
                .filter(user -> "mentor".equalsIgnoreCase(user.getRole()))
                .filter(user -> normalizeSlug(buildFullName(user)).equals(item.slug()))
                .findFirst()
                .flatMap(user -> mentorProfileRepository.findById(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Mentor review item not found."));
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

    public record MentorReviewItemView(
            String slug,
            String name,
            String roleAtCompany,
            String bio,
            List<String> skills,
            List<String> interviewCompanies,
            String reviewStatus,
            String statusClass,
            String adminNote
    ) {
    }
}
