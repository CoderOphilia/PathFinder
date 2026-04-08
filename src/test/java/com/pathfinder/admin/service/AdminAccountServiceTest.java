package com.pathfinder.admin.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminAccountService adminAccountService;

    @Test
    // Maps saved users into simple admin table rows.
    void listUsers() {
        when(userRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "role", "email")))
                .thenReturn(List.of(
                        createUser(1L, "mentee@example.com", "mentee", "ACTIVE"),
                        createUser(2L, "mentor@example.com", "mentor", "SUSPENDED")
                ));

        List<AdminAccountService.ManagedUserView> result = adminAccountService.listUsers();

        assertEquals(2, result.size());
        assertEquals("Mentee", result.getFirst().role());
        assertTrue(result.getFirst().canSuspend());
        assertTrue(result.get(1).canReactivate());
    }

    @Test
    // Suspends an active non-admin account.
    void suspendUser() {
        User user = createUser(1L, "mentee@example.com", "mentee", "ACTIVE");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        adminAccountService.suspendUser(1L);

        assertEquals("SUSPENDED", user.getAccountStatus());
    }

    @Test
    // Reactivates a suspended non-admin account.
    void reactivateUser() {
        User user = createUser(1L, "mentor@example.com", "mentor", "SUSPENDED");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        adminAccountService.reactivateUser(1L);

        assertEquals("ACTIVE", user.getAccountStatus());
    }

    @Test
    // Blocks admin accounts from being suspended.
    void blockAdminSuspension() {
        User user = createUser(1L, "admin@example.com", "admin", "ACTIVE");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                adminAccountService.suspendUser(1L)
        );

        assertTrue(exception.getMessage().contains("Admin accounts"));
    }

    private User createUser(Long id, String email, String role, String status) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setAccountStatus(status);
        user.setFirstName("Test");
        user.setLastName("User");
        return user;
    }
}
