package com.pathfinder.admin.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import com.pathfinder.mentor.domain.MentorInterviewCompany;
import com.pathfinder.mentor.domain.MentorProfile;
import com.pathfinder.mentor.domain.MentorSkill;
import com.pathfinder.mentor.domain.VerificationStatus;
import com.pathfinder.mentor.repo.MentorInterviewCompanyRepository;
import com.pathfinder.mentor.repo.MentorSkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReviewServiceTest {

    @Mock
    private AdminReviewMentorProfileResolver mentorProfileResolver;

    @Mock
    private MentorSkillRepository mentorSkillRepository;

    @Mock
    private MentorInterviewCompanyRepository mentorInterviewCompanyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminReviewService adminReviewService;

    @Test
    // Builds mentor review rows from persisted mentor/profile data.
    void listReviewItems() {
        User mentor = createMentorUser();
        MentorProfile profile = createProfile(VerificationStatus.PENDING, "");
        MentorSkill skill = new MentorSkill();
        skill.setSkillName("System design");
        MentorInterviewCompany company = new MentorInterviewCompany();
        company.setCompanyName("Meta");

        when(userRepository.findAll()).thenReturn(List.of(mentor));
        when(mentorProfileResolver.findExistingOrDefault(mentor)).thenReturn(profile);
        when(mentorSkillRepository.findByMentorProfileUserIdOrderBySkillNameAsc(1L)).thenReturn(List.of(skill));
        when(mentorInterviewCompanyRepository.findByMentorProfileUserIdOrderByCompanyNameAsc(1L)).thenReturn(List.of(company));

        AdminReviewService.MentorReviewSummaryView item = adminReviewService.listReviewItems().getFirst();

        assertEquals("mentor-user", item.slug());
        assertEquals("Pending review", item.reviewStatus());
        assertEquals("Complete (7/7)", item.verificationSummary());
        assertEquals("7/7", item.verificationScore());
    }

    @Test
    // Builds the full detail view used by the dedicated mentor review page.
    void findReviewItem() {
        User mentor = createMentorUser();
        MentorProfile profile = createProfile(VerificationStatus.PENDING, "");
        MentorSkill skill = new MentorSkill();
        skill.setSkillName("System design");
        MentorInterviewCompany company = new MentorInterviewCompany();
        company.setCompanyName("Meta");

        when(userRepository.findAll()).thenReturn(List.of(mentor));
        when(mentorProfileResolver.findExistingOrDefault(mentor)).thenReturn(profile);
        when(mentorSkillRepository.findByMentorProfileUserIdOrderBySkillNameAsc(1L)).thenReturn(List.of(skill));
        when(mentorInterviewCompanyRepository.findByMentorProfileUserIdOrderByCompanyNameAsc(1L)).thenReturn(List.of(company));

        AdminReviewService.MentorReviewDetailView item = adminReviewService.findReviewItem("mentor-user");

        assertEquals("mentor@example.com", item.email());
        assertEquals("System design", item.skills().getFirst());
        assertEquals("Meta", item.interviewCompanies().getFirst());
        assertEquals("Complete (7/7)", item.verificationSummary());
    }

    @Test
    void listReviewItemsIncludesMentorWithoutSavedProfile() {
        User mentor = createMentorUser();
        MentorProfile profile = createEmptyProfile();

        when(userRepository.findAll()).thenReturn(List.of(mentor));
        when(mentorProfileResolver.findExistingOrDefault(mentor)).thenReturn(profile);
        when(mentorSkillRepository.findByMentorProfileUserIdOrderBySkillNameAsc(1L)).thenReturn(List.of());
        when(mentorInterviewCompanyRepository.findByMentorProfileUserIdOrderByCompanyNameAsc(1L)).thenReturn(List.of());

        AdminReviewService.MentorReviewSummaryView item = adminReviewService.listReviewItems().getFirst();

        assertEquals("mentor-user", item.slug());
        assertEquals("Pending review", item.reviewStatus());
        assertEquals("0/7 complete • Missing Current title, Current company, Expertise, Bio, Hourly rate, Skills, Interview companies", item.verificationSummary());
        assertEquals("0/7", item.verificationScore());
    }

    @Test
    void findReviewItemIncludesMentorWithoutSavedProfile() {
        User mentor = createMentorUser();
        MentorProfile profile = createEmptyProfile();

        when(userRepository.findAll()).thenReturn(List.of(mentor));
        when(mentorProfileResolver.findExistingOrDefault(mentor)).thenReturn(profile);
        when(mentorSkillRepository.findByMentorProfileUserIdOrderBySkillNameAsc(1L)).thenReturn(List.of());
        when(mentorInterviewCompanyRepository.findByMentorProfileUserIdOrderByCompanyNameAsc(1L)).thenReturn(List.of());

        AdminReviewService.MentorReviewDetailView item = adminReviewService.findReviewItem("mentor-user");

        assertEquals("mentor@example.com", item.email());
        assertEquals("Pending review", item.reviewStatus());
        assertEquals("0/7 complete • Missing Current title, Current company, Expertise, Bio, Hourly rate, Skills, Interview companies", item.verificationSummary());
    }

    @Test
    // Stores an approved decision on the mentor profile.
    void approveMentor() {
        User mentor = createMentorUser();
        MentorProfile profile = createProfile(VerificationStatus.PENDING, "");

        mockMentorLookup(mentor, profile);

        adminReviewService.approveMentor("mentor-user", "Looks good.");

        assertEquals(VerificationStatus.APPROVED, profile.getVerificationStatus());
        assertEquals("Looks good.", profile.getAdminNote());
    }

    @Test
    // Stores a denied decision on the mentor profile.
    void denyMentor() {
        User mentor = createMentorUser();
        MentorProfile profile = createProfile(VerificationStatus.PENDING, "");

        mockMentorLookup(mentor, profile);

        adminReviewService.denyMentor("mentor-user", "Profile is incomplete.");

        assertEquals(VerificationStatus.REJECTED, profile.getVerificationStatus());
        assertEquals("Profile is incomplete.", profile.getAdminNote());
    }

    @Test
    // Prevents admins from rejecting a mentor without leaving a reason.
    void denyMentorRequiresNote() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> adminReviewService.denyMentor("mentor-user", "   ")
        );

        assertEquals("Enter a denial note before rejecting the mentor.", exception.getMessage());
    }

    private void mockMentorLookup(User mentor, MentorProfile profile) {
        when(userRepository.findAll()).thenReturn(List.of(mentor));
        when(mentorProfileResolver.findExistingOrCreate(mentor)).thenReturn(profile);
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

    private MentorProfile createProfile(VerificationStatus status, String adminNote) {
        MentorProfile profile = new MentorProfile();
        profile.setUserId(1L);
        profile.setCurrentTitle("Staff Engineer");
        profile.setCurrentCompany("Example");
        profile.setExpertise("Backend interviews");
        profile.setBio("Experienced mentor");
        profile.setHourlyRateCents(8000);
        profile.setVerificationStatus(status);
        profile.setAdminNote(adminNote);
        return profile;
    }

    private MentorProfile createEmptyProfile() {
        MentorProfile profile = new MentorProfile();
        profile.setUserId(1L);
        profile.setVerificationStatus(VerificationStatus.PENDING);
        profile.setAdminNote("");
        profile.setHourlyRateCents(0);
        profile.setSessionsCompleted(0);
        return profile;
    }
}
