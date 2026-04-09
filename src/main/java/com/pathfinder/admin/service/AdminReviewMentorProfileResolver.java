package com.pathfinder.admin.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.mentor.domain.MentorProfile;
import com.pathfinder.mentor.domain.VerificationStatus;
import com.pathfinder.mentor.repo.MentorProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class AdminReviewMentorProfileResolver {

    private final MentorProfileRepository mentorProfileRepository;

    AdminReviewMentorProfileResolver(MentorProfileRepository mentorProfileRepository) {
        this.mentorProfileRepository = mentorProfileRepository;
    }

    @Transactional(readOnly = true)
    MentorProfile findExistingOrDefault(User mentorUser) {
        return mentorProfileRepository.findById(mentorUser.getId())
                .orElseGet(() -> buildEmptyProfile(mentorUser));
    }

    MentorProfile findExistingOrCreate(User mentorUser) {
        return mentorProfileRepository.findById(mentorUser.getId())
                .orElseGet(() -> mentorProfileRepository.save(buildEmptyProfile(mentorUser)));
    }

    private MentorProfile buildEmptyProfile(User mentorUser) {
        MentorProfile profile = new MentorProfile();
        profile.setUserId(mentorUser.getId());
        profile.setUser(mentorUser);
        profile.setVerificationStatus(VerificationStatus.PENDING);
        profile.setAdminNote("");
        profile.setHourlyRateCents(0);
        profile.setSessionsCompleted(0);
        return profile;
    }
}
