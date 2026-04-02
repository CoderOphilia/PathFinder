package com.pathfinder.mentor;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import com.pathfinder.auth.service.UserService;
import com.pathfinder.mentor.domain.MentorInterviewCompany;
import com.pathfinder.mentor.domain.MentorProfile;
import com.pathfinder.mentor.domain.MentorSkill;
import com.pathfinder.mentor.repo.MentorAvailabilityRepository;
import com.pathfinder.mentor.repo.MentorAvailabilitySettingsRepository;
import com.pathfinder.mentor.repo.MentorBlockedDateRepository;
import com.pathfinder.mentor.repo.MentorInterviewCompanyRepository;
import com.pathfinder.mentor.repo.MentorProfileRepository;
import com.pathfinder.mentor.repo.MentorSkillRepository;
import com.pathfinder.mentor.service.MentorProfileService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    private MentorAvailabilitySettingsRepository mentorAvailabilitySettingsRepository;

    @Mock
    private MentorBlockedDateRepository mentorBlockedDateRepository;

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

    private User mentorUser;

    @BeforeEach
    void setUp() {
        mentorUser = new User();
        mentorUser.setId(1L);
        mentorUser.setFirstName("Alex");
        mentorUser.setLastName("Kim");
        mentorUser.setEmail("mentor@example.com");
        mentorUser.setRole("mentor");
    }

    @Test
    // Saving with an email that does not exist should throw an exception.
    void saveProfileNoUser() {
        when(userService.findUserByEmail("missing@example.com")).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                mentorProfileService.saveProfile(
                        "missing@example.com",
                        "Missing User",
                        "Java",
                        "80",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        ""
                ));

        assertEquals("No user exists for that email.", exception.getMessage());
        verify(entityManager, never()).persist(any());
    }

    @Test
    // Saving should be blocked when the user is not a mentor.
    void saveProfileNotMentor() {
        mentorUser.setRole("mentee");
        when(userService.findUserByEmail("mentor@example.com")).thenReturn(mentorUser);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                mentorProfileService.saveProfile(
                        "mentor@example.com",
                        "Alex Kim",
                        "Java",
                        "80",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        ""
                ));

        assertEquals("That account is not registered as a mentor.", exception.getMessage());
        verify(entityManager, never()).persist(any());
    }

    @Test
    // An invalid hourly rate should throw an exception.
    void saveProfileBadRate() {
        when(userService.findUserByEmail("mentor@example.com")).thenReturn(mentorUser);
        when(userRepository.getReferenceById(1L)).thenReturn(mentorUser);
        when(mentorProfileRepository.findById(1L)).thenReturn(Optional.of(new MentorProfile()));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                mentorProfileService.saveProfile(
                        "mentor@example.com",
                        "Alex Kim",
                        "Java",
                        "eighty",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        ""
                ));

        assertEquals("Hourly rate must be a valid number.", exception.getMessage());
    }

    @Test
    // Valid input should save the profile and related mentor data.
    void saveProfileOk() {
        when(userService.findUserByEmail("mentor@example.com")).thenReturn(mentorUser);
        when(userRepository.getReferenceById(1L)).thenReturn(mentorUser);
        when(mentorProfileRepository.findById(1L)).thenReturn(Optional.empty());

        MentorProfile savedProfile = mentorProfileService.saveProfile(
                "mentor@example.com",
                "Alex Kim",
                "Java, Spring",
                "80",
                "Backend mentor",
                "Technology",
                "America/Vancouver",
                "Senior Engineer",
                "Example Corp",
                "Google, Amazon",
                "Bio text"
        );

        ArgumentCaptor<MentorProfile> profileCaptor = ArgumentCaptor.forClass(MentorProfile.class);
        ArgumentCaptor<List<MentorSkill>> skillCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<MentorInterviewCompany>> companyCaptor = ArgumentCaptor.forClass(List.class);

        verify(entityManager).persist(profileCaptor.capture());
        verify(mentorSkillRepository).deleteByMentorProfileUserId(1L);
        verify(mentorInterviewCompanyRepository).deleteByMentorProfileUserId(1L);
        verify(mentorSkillRepository).saveAll(skillCaptor.capture());
        verify(mentorInterviewCompanyRepository).saveAll(companyCaptor.capture());

        MentorProfile persistedProfile = profileCaptor.getValue();
        assertNotNull(savedProfile);
        assertEquals(1L, persistedProfile.getUserId());
        assertEquals("Alex", mentorUser.getFirstName());
        assertEquals("Kim", mentorUser.getLastName());
        assertEquals("Java, Spring", persistedProfile.getExpertise());
        assertEquals(8000, persistedProfile.getHourlyRateCents());
        assertEquals("Backend mentor", persistedProfile.getTagline());
        assertEquals("Technology", persistedProfile.getIndustry());
        assertEquals("America/Vancouver", persistedProfile.getTimezone());
        assertEquals("Senior Engineer", persistedProfile.getCurrentTitle());
        assertEquals("Example Corp", persistedProfile.getCurrentCompany());
        assertEquals("Bio text", persistedProfile.getBio());

        List<MentorSkill> savedSkills = skillCaptor.getValue();
        assertEquals(2, savedSkills.size());
        assertEquals("Java", savedSkills.get(0).getSkillName());
        assertEquals("Spring", savedSkills.get(1).getSkillName());

        List<MentorInterviewCompany> savedCompanies = companyCaptor.getValue();
        assertEquals(2, savedCompanies.size());
        assertEquals("Google", savedCompanies.get(0).getCompanyName());
        assertEquals("Amazon", savedCompanies.get(1).getCompanyName());
    }
}
