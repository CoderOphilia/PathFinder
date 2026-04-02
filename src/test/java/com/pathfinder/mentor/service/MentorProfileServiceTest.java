package com.pathfinder.mentor.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import com.pathfinder.auth.service.UserService;
import com.pathfinder.mentor.domain.MentorAvailability;
import com.pathfinder.mentor.domain.MentorProfile;
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
                "Senior Engineer",
                "Example Corp",
                "Google, Amazon",
                "Backend mentor"
        );

        assertEquals(1L, result.getUserId());
        assertEquals("Senior Engineer", result.getCurrentTitle());
        assertEquals("Example Corp", result.getCurrentCompany());
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
