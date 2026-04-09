package com.pathfinder.admin.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.mentor.domain.MentorProfile;
import com.pathfinder.mentor.domain.VerificationStatus;
import com.pathfinder.mentor.repo.MentorProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReviewMentorProfileResolverTest {

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @InjectMocks
    private AdminReviewMentorProfileResolver resolver;

    @Test
    void findExistingOrDefaultBuildsPendingProfileWhenMissing() {
        User mentor = createMentorUser();
        when(mentorProfileRepository.findById(1L)).thenReturn(Optional.empty());

        MentorProfile profile = resolver.findExistingOrDefault(mentor);

        assertEquals(1L, profile.getUserId());
        assertSame(mentor, profile.getUser());
        assertEquals(VerificationStatus.PENDING, profile.getVerificationStatus());
        assertEquals("", profile.getAdminNote());
        assertEquals(0, profile.getHourlyRateCents());
        assertEquals(0, profile.getSessionsCompleted());
    }

    @Test
    void findExistingOrCreateSavesPendingProfileWhenMissing() {
        User mentor = createMentorUser();
        when(mentorProfileRepository.findById(1L)).thenReturn(Optional.empty());
        when(mentorProfileRepository.save(any(MentorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MentorProfile profile = resolver.findExistingOrCreate(mentor);

        assertEquals(1L, profile.getUserId());
        assertSame(mentor, profile.getUser());
        assertEquals(VerificationStatus.PENDING, profile.getVerificationStatus());
        assertEquals("", profile.getAdminNote());
    }

    @Test
    void findExistingOrDefaultReturnsSavedProfileWhenPresent() {
        User mentor = createMentorUser();
        MentorProfile existing = new MentorProfile();
        existing.setUserId(1L);
        existing.setVerificationStatus(VerificationStatus.APPROVED);

        when(mentorProfileRepository.findById(1L)).thenReturn(Optional.of(existing));

        MentorProfile profile = resolver.findExistingOrDefault(mentor);

        assertSame(existing, profile);
    }

    private User createMentorUser() {
        User mentor = new User();
        mentor.setId(1L);
        mentor.setEmail("mentor@example.com");
        mentor.setRole("mentor");
        mentor.setFirstName("Mentor");
        mentor.setLastName("User");
        return mentor;
    }
}
