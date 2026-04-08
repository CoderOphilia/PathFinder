package com.pathfinder.mentee.dto;

import java.util.List;

public record MentorDirectoryItemView(
        String slug,
        String name,
        String profileImageUrl,
        String rate,
        boolean offersFreeSession,
        String trialSessionLabel,
        String roleAtCompany,
        String tagline,
        List<String> skills,
        List<String> interviewCompanies,
        int sessionsCompleted
) {
}
