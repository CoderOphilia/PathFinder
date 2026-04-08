package com.pathfinder.admin.service;

import com.pathfinder.admin.domain.AdminProfile;
import com.pathfinder.admin.repo.AdminProfileRepository;
import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminProfileServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminProfileRepository adminProfileRepository = mock(AdminProfileRepository.class);
    private final AdminProfileService service = new AdminProfileService(userRepository, adminProfileRepository);

    @Test
    void saveProfileCreatesProfileForAdminUser() {
        User admin = adminUser();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(adminProfileRepository.findById(7L)).thenReturn(Optional.empty());
        when(adminProfileRepository.save(any(AdminProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminProfile saved = service.saveProfile("admin@example.com", "Trust and Safety", "#ops", "Covers mentor moderation");

        assertEquals(7L, saved.getUserId());
        assertEquals("Trust and Safety", saved.getTeam());
        assertEquals("#ops", saved.getSupportChannel());
        assertEquals("Covers mentor moderation", saved.getNotes());
        verify(adminProfileRepository).save(any(AdminProfile.class));
    }

    @Test
    void findProfileByEmailReturnsNullForNonAdminUser() {
        User mentor = new User();
        mentor.setId(8L);
        mentor.setEmail("mentor@example.com");
        mentor.setRole("mentor");
        when(userRepository.findByEmail("mentor@example.com")).thenReturn(Optional.of(mentor));

        assertNull(service.findProfileByEmail("mentor@example.com"));
    }

    @Test
    void findProfileByEmailReturnsSavedProfile() {
        User admin = adminUser();
        AdminProfile profile = new AdminProfile();
        profile.setUserId(7L);
        profile.setTeam("Operations");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(adminProfileRepository.findById(7L)).thenReturn(Optional.of(profile));

        AdminProfile loaded = service.findProfileByEmail("admin@example.com");

        assertNotNull(loaded);
        assertEquals("Operations", loaded.getTeam());
    }

    private User adminUser() {
        User admin = new User();
        admin.setId(7L);
        admin.setEmail("admin@example.com");
        admin.setRole("admin");
        admin.setFirstName("Admin");
        admin.setLastName("User");
        return admin;
    }
}
