package com.pathfinder.admin.service;

import com.pathfinder.mentor.web.DemoMentorCatalog;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AdminReviewService {

    private final DemoMentorCatalog mentorCatalog;
    private final Map<String, ReviewDecisionState> decisionsByMentorSlug = new HashMap<>();

    public AdminReviewService(DemoMentorCatalog mentorCatalog) {
        this.mentorCatalog = mentorCatalog;
    }

    public synchronized List<MentorReviewItemView> listReviewItems() {
        return mentorCatalog.listMentors().stream()
                .map(this::toView)
                .toList();
    }

    public synchronized Optional<MentorReviewItemView> findReviewItem(String mentorSlug) {
        if (isBlank(mentorSlug)) {
            return Optional.empty();
        }
        return listReviewItems().stream()
                .filter(item -> item.mentor().slug().equalsIgnoreCase(mentorSlug.trim()))
                .findFirst();
    }

    public synchronized void approveMentor(String mentorSlug, String adminNote) {
        String normalizedSlug = requireSlug(mentorSlug);
        decisionsByMentorSlug.put(
                normalizedSlug,
                new ReviewDecisionState(ReviewOutcome.APPROVED, normalizeText(adminNote))
        );
    }

    public synchronized void requestUpdates(String mentorSlug, String adminNote) {
        String normalizedSlug = requireSlug(mentorSlug);
        decisionsByMentorSlug.put(
                normalizedSlug,
                new ReviewDecisionState(ReviewOutcome.CHANGES_REQUESTED, normalizeText(adminNote))
        );
    }

    public synchronized long pendingReviewCount() {
        return listReviewItems().stream()
                .filter(item -> "Pending review".equals(item.reviewStatus()))
                .count();
    }

    private MentorReviewItemView toView(DemoMentorCatalog.MentorCatalogItem mentor) {
        ReviewDecisionState decisionState = decisionsByMentorSlug.get(normalizeSlug(mentor.slug()));
        if (decisionState == null || decisionState.outcome() == ReviewOutcome.PENDING) {
            return new MentorReviewItemView(
                    mentor,
                    "Pending review",
                    "Pending review",
                    "statusBadge statusBadge--requested",
                    ""
            );
        }

        if (decisionState.outcome() == ReviewOutcome.APPROVED) {
            return new MentorReviewItemView(
                    mentor,
                    "Approved",
                    "Approved",
                    "statusBadge statusBadge--approved",
                    defaultIfBlank(decisionState.adminNote(), "Mentor verification approved.")
            );
        }

        return new MentorReviewItemView(
                mentor,
                "Changes requested",
                "Changes requested",
                "statusBadge statusBadge--declined",
                defaultIfBlank(decisionState.adminNote(), "Please update your profile before approval.")
        );
    }

    private String requireSlug(String mentorSlug) {
        String normalizedSlug = normalizeSlug(mentorSlug);
        boolean exists = mentorCatalog.listMentors().stream()
                .anyMatch(item -> item.slug().equalsIgnoreCase(normalizedSlug));
        if (!exists) {
            throw new IllegalArgumentException("Mentor review item not found.");
        }
        return normalizedSlug;
    }

    private String normalizeSlug(String mentorSlug) {
        return mentorSlug == null ? "" : mentorSlug.trim().toLowerCase();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private record ReviewDecisionState(
            ReviewOutcome outcome,
            String adminNote
    ) {
    }

    private enum ReviewOutcome {
        PENDING,
        APPROVED,
        CHANGES_REQUESTED
    }

    public record MentorReviewItemView(
            DemoMentorCatalog.MentorCatalogItem mentor,
            String reviewStatus,
            String statusLabel,
            String statusClass,
            String adminNote
    ) {
    }
}
