package com.pathfinder.mentor.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import com.pathfinder.auth.service.UserService;
import com.pathfinder.mentor.domain.MentorAvailability;
import com.pathfinder.mentor.domain.MentorProfile;
import com.pathfinder.mentor.domain.VerificationStatus;
import com.pathfinder.mentor.repo.MentorAvailabilityRepository;
import com.pathfinder.mentor.repo.MentorInterviewCompanyRepository;
import com.pathfinder.mentor.repo.MentorProfileRepository;
import com.pathfinder.mentor.repo.MentorSkillRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorProfileServiceTest {

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private MentorAvailabilityRepository mentorAvailabilityRepository;

    @Mock
    private MentorInterviewCompanyRepository mentorInterviewCompanyRepository;

    @Mock
    private MentorSkillRepository mentorSkillRepository;

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private MentorProfileService mentorProfileService;

    @Test
    // Saves a mentor profile.
    void saveProfile() {
        User mentor = createMentorUser();

        when(userService.findUserByEmail("mentor@example.com")).thenReturn(mentor);
        when(userRepository.getReferenceById(1L)).thenReturn(mentor);
        when(mentorProfileRepository.findById(1L)).thenReturn(Optional.empty());

        MentorProfile result = mentorProfileService.saveProfile(
                "mentor@example.com",
                "Mentor User",
                "Java, Spring",
                "80",
                true,
                "tue",
                "18:00",
                "19:00",
                "Senior Engineer",
                "Example Corp",
                "Google, Amazon",
                "Backend mentor"
        );

        assertEquals(1L, result.getUserId());
        assertEquals("Senior Engineer", result.getCurrentTitle());
        assertEquals("Example Corp", result.getCurrentCompany());
        assertEquals(true, result.isOffersFreeSession());
        assertEquals(3, result.getTrialSessionWeekday());
        verify(entityManager).persist(any(MentorProfile.class));
    }

    @Test
    // Rejects saving a profile for a non-mentor user.
    void rejectNonMentor() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setRole("seeker");

        when(userService.findUserByEmail("user@example.com")).thenReturn(user);

        assertThrows(IllegalArgumentException.class, () ->
                mentorProfileService.saveProfile(
                        "user@example.com",
                        "Normal User",
                        "Java",
                        "80",
                        false,
                        "",
                        "",
                        "",
                        "Engineer",
                        "Example Corp",
                        "",
                        ""
                )
        );

        verify(entityManager, never()).persist(any(MentorProfile.class));
    }

    @Test
    // Saves mentor availability rows.
    void saveAvailability() {
        User mentor = createMentorUser();
        MentorProfile profile = new MentorProfile();
        profile.setUserId(1L);
        profile.setUser(mentor);

        when(userService.findUserByEmail("mentor@example.com")).thenReturn(mentor);
        when(mentorProfileRepository.findById(1L)).thenReturn(Optional.of(profile));

        assertDoesNotThrow(() -> mentorProfileService.replaceAvailability(
                "mentor@example.com",
                List.of(
                        new MentorProfileService.AvailabilityInput(2, "18:00", "20:00"),
                        new MentorProfileService.AvailabilityInput(4, "17:00", "19:00")
                )
        ));

        verify(mentorAvailabilityRepository).deleteByMentorProfileUserId(1L);
        verify(mentorAvailabilityRepository).saveAll(any());
    }

    @Test
    // Loads saved mentor availability.
    void loadAvailability() {
        User mentor = createMentorUser();
        MentorProfile profile = new MentorProfile();
        profile.setUserId(1L);
        profile.setUser(mentor);

        MentorAvailability availability = new MentorAvailability();
        availability.setMentorProfile(profile);
        availability.setWeekday(2);
        availability.setStartTime(java.time.LocalTime.of(18, 0));
        availability.setEndTime(java.time.LocalTime.of(20, 0));

        when(userService.findUserByEmail("mentor@example.com")).thenReturn(mentor);
        when(mentorAvailabilityRepository.findByMentorProfileUserIdOrderByWeekdayAscStartTimeAsc(anyLong()))
                .thenReturn(List.of(availability));

        List<MentorProfileService.AvailabilityInput> result =
                mentorProfileService.findAvailabilityByEmail("mentor@example.com");

        assertEquals(1, result.size());
        assertEquals(2, result.getFirst().weekday());
        assertEquals("18:00", result.getFirst().startTime());
        assertEquals("20:00", result.getFirst().endTime());
    }

    @Test
    void initializeProfileForNewMentorCreatesPlaceholderProfile() {
        User mentor = createMentorUser();

        when(mentorProfileRepository.findById(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(userRepository.getReferenceById(1L)).thenReturn(mentor);

        mentorProfileService.initializeProfileForNewMentor(1L);

        verify(entityManager).persist(any(MentorProfile.class));
    }

    @Test
    void listPublicMentorsOnlyReturnsApprovedMentors() {
        User approvedMentor = createMentorUser();
        User pendingMentor = new User();
        pendingMentor.setId(2L);
        pendingMentor.setEmail("pending@example.com");
        pendingMentor.setRole("mentor");
        pendingMentor.setFirstName("Pending");
        pendingMentor.setLastName("Mentor");

        when(userRepository.findAll()).thenReturn(List.of(approvedMentor, pendingMentor));
        when(mentorProfileRepository.findById(1L)).thenReturn(Optional.of(createProfile(VerificationStatus.APPROVED)));
        when(mentorProfileRepository.findById(2L)).thenReturn(Optional.of(createProfile(VerificationStatus.PENDING)));
        when(mentorSkillRepository.findByMentorProfileUserIdOrderBySkillNameAsc(anyLong())).thenReturn(List.of());
        when(mentorInterviewCompanyRepository.findByMentorProfileUserIdOrderByCompanyNameAsc(anyLong())).thenReturn(List.of());

        List<MentorProfileService.PublicMentorProfile> mentors = mentorProfileService.listPublicMentors();

        assertEquals(1, mentors.size());
        assertEquals("Mentor User", mentors.getFirst().name());
    }

    @Test
    void publicProfileLookupIgnoresUnapprovedMentors() {
        User mentor = createMentorUser();

        when(userRepository.findAll()).thenReturn(List.of(mentor));
        when(mentorProfileRepository.findById(1L)).thenReturn(Optional.of(createProfile(VerificationStatus.REJECTED)));

        MentorProfileService.PublicMentorProfile result = mentorProfileService.findPublicProfileBySlug("mentor-user");

        assertEquals(null, result);
    }

    @Test
    void mentorEmailLookupOnlyReturnsApprovedMentors() {
        User mentor = createMentorUser();

        when(userRepository.findAll()).thenReturn(List.of(mentor));
        when(mentorProfileRepository.findById(1L)).thenReturn(Optional.of(createProfile(VerificationStatus.PENDING)));

        String mentorEmail = mentorProfileService.findMentorEmailByName("Mentor User");

        assertEquals("", mentorEmail);
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

    private MentorProfile createProfile(VerificationStatus verificationStatus) {
        MentorProfile profile = new MentorProfile();
        profile.setUserId(1L);
        profile.setVerificationStatus(verificationStatus);
        profile.setCurrentTitle("Senior Engineer");
        profile.setCurrentCompany("Example Corp");
        profile.setBio("Backend mentor");
        profile.setHourlyRateCents(8000);
        profile.setSessionsCompleted(12);
        return profile;
    }
}
