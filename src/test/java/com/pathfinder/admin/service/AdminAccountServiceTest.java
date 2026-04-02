package com.pathfinder.admin.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@Import(AdminAccountService.class)
class AdminAccountServiceTest {

    @Autowired
    private AdminAccountService adminAccountService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void suspendUserChangesAccountStatusToSuspended() {
        User user = userRepository.save(createUser("seeker@example.com", "mentee", "ACTIVE"));

        adminAccountService.suspendUser(user.getId());

        assertEquals("SUSPENDED", userRepository.findById(user.getId()).orElseThrow().getAccountStatus());
    }

    @Test
    void reactivateUserChangesAccountStatusToActive() {
        User user = userRepository.save(createUser("mentor@example.com", "mentor", "SUSPENDED"));

        adminAccountService.reactivateUser(user.getId());

        assertEquals("ACTIVE", userRepository.findById(user.getId()).orElseThrow().getAccountStatus());
    }

    private User createUser(String email, String role, String accountStatus) {
        User user = new User();
        user.setFirstName("Demo");
        user.setLastName("User");
        user.setEmail(email);
        user.setPassword("encoded-password");
        user.setRole(role);
        user.setAccountStatus(accountStatus);
        return user;
    }
}
