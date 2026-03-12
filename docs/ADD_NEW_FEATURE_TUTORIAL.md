# PathFinder Tutorial: How to Add a New Feature

This guide is for teammates who are new to Java and Spring Boot.
It shows the same pattern already used in this project so you can add features safely.

## 1. How features were added in this codebase

From the current code and commit history, feature work followed this order:

1. Landing page was added first (`/` route + Thymeleaf page).
2. Project was reorganized by feature packages (`auth`, `mentor`, `seeker`, `session`, `admin`).
3. Auth feature was added with:
   - `AuthController` in `src/main/java/com/pathfinder/auth/web/`
   - Pages in `src/main/resources/templates/auth/`
   - Shared `layout.html` + navbar fragments.
4. UI was improved in `app.css` and auth/landing templates without changing route structure.

The key idea: **thin controller + Thymeleaf page + shared layout/fragments**.

## 2. Current pattern you should follow

### Java package structure

Each feature has this structure:

- `web` -> MVC controllers
- `service` -> business logic (planned/optional for simple pages)
- `repo` -> database access (planned)
- `domain` -> entities/models (planned)
- `dto` -> data transfer objects (planned)

### View rendering pattern

Controllers return `"layout"` and pass fragment names into the model:

```java
model.addAttribute("title", "Page Title");
model.addAttribute("navbarType", "fragments/navbar :: navbar");
model.addAttribute("content", "feature/page :: content");
return "layout";
```

Then `layout.html` inserts:

- navbar fragment from `navbarType`
- page fragment from `content`

## 3. Add a simple feature: "Mentor Directory" page

This example adds one new route and one new page.

### Step A: Create a controller

Create file:
`src/main/java/com/pathfinder/mentor/web/MentorController.java`

```java
package com.pathfinder.mentor.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/mentors")
public class MentorController {

    @GetMapping
    public String listMentors(Model model) {
        model.addAttribute("title", "Mentor Directory");
        model.addAttribute("navbarType", "fragments/navbar :: navbar");
        model.addAttribute("content", "mentor/index :: content");
        return "layout";
    }
}
```

What this does:

- Route `GET /mentors` is created.
- It uses the same layout system as landing/auth.

### Step B: Create the Thymeleaf page

Create file:
`src/main/resources/templates/mentor/index.html`

```html
<section xmlns:th="http://www.thymeleaf.org" th:fragment="content">
	<div class="gridPage">
		<section class="tile tile--full" aria-label="Mentor directory">
			<p class="tile__kicker">Mentors</p>
			<h1>Mentor Directory</h1>
			<p class="p p--muted p--tight">
				This is a starter page. Real mentor data can be connected later.
			</p>
		</section>

		<section class="tile tile--span6">
			<h2>Sample Mentor</h2>
			<p class="p p--muted p--tight">Alex M. - Backend Java, Interview Prep</p>
		</section>

		<section class="tile tile--span6">
			<h2>Sample Mentor</h2>
			<p class="p p--muted p--tight">Priya K. - Spring, System Design</p>
		</section>
	</div>
</section>
```

Important: page content must be in `th:fragment="content"` so layout can inject it.

### Step C: Add link in navbar

Edit:
`src/main/resources/templates/fragments/navbar.html`

Replace the disabled mentors link:

```html
<a th:href="@{/mentors}" class="nav__link">Mentors</a>
```

### Step D: Run and test

Run app:

```bash
mvn spring-boot:run
```

Check:

- `http://localhost:8080/` works
- `http://localhost:8080/mentors` opens your new page
- Navbar link opens mentor page

## 4. Rules to keep your feature consistent

1. Keep controllers simple: route + model attributes + `return "layout"`.
2. Put feature pages under `templates/<feature-name>/`.
3. Use fragments (`th:fragment="content"`) in every page template.
4. Reuse existing CSS classes from `src/main/resources/static/css/app.css`.
5. Start with static/demo data first; add DB logic later.

## 5. Common mistakes (and fixes)

1. White/blank page:
   - Cause: missing `th:fragment="content"` in template.
   - Fix: wrap page content in a section with `th:fragment="content"`.
2. Template not found error:
   - Cause: wrong value in `model.addAttribute("content", "...")`.
   - Fix: make sure it matches `templates/<folder>/<file> :: content`.
3. New route returns 404:
   - Cause: missing `@Controller`, wrong `@RequestMapping`, or wrong `@GetMapping`.
   - Fix: compare with `AuthController` and `LandingController`.

## 6. Next level (when backend is added)

After adding Spring Data JPA, each feature can follow this order:

1. `domain` entity class
2. `repo` interface
3. `service` class
4. `web` controller methods
5. Thymeleaf template updates

For now, PathFinder is following a view-first approach, so building pages/routes first is the correct workflow.

## 7. Remaining feature roadmap

Use this order and do one small task at a time.

### Phase 1: Real auth first

1. Save signup data to MySQL.
2. Hash passwords.
3. Make login validate email + password from DB.
4. Add roles (`JOB_SEEKER`, `MENTOR`, `ADMIN`).

### Phase 2: Job seeker feature

1. Profile create/edit page.
2. Mentor listing page.
3. Session request page.
4. Session history page.
5. Action items page (mark complete).

### Phase 3: Mentor feature

1. Mentor profile page (expertise + pricing).
2. Availability slots page.
3. Approve/decline requests page.
4. Session summary + action items page.
5. Earnings summary page.

### Phase 4: Admin feature

1. Verify mentors page.
2. Suspend/reactivate users page.
3. Basic platform overview page.

### Phase 5: Sessions + payments

1. Add session state transitions:
   - `requested`
   - `approved` or `declined`
   - `completed` or `cancelled`
2. Save booking notes.
3. Save post-session summaries and action items.
4. Save payment status (`unpaid`, `paid`, `refunded`).
5. Add simple transaction history.

### Not needed for MVP

1. Chat/messaging
2. Built-in video calling
3. AI mentor matching
4. Job board

## 8. Backend implementation checklist

Use this sequence for backend development.

### Phase A: Data and migration foundation

1. Configure MySQL and Flyway.
2. Create baseline schema migrations.
3. Define shared enums for role and status values.
4. Verification target:
   - Application starts and Flyway migrations complete successfully.

Example `application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/pathfinder?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.format_sql=true

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

Example migration file:
`src/main/resources/db/migration/V1__create_users.sql`

```sql
create table users (
    id bigint not null auto_increment,
    email varchar(255) not null,
    password_hash varchar(255) not null,
    role varchar(30) not null,
    is_active bit not null default 1,
    primary key (id),
    unique key uk_users_email (email)
);
```

### Phase B: Authentication and authorization

1. Create user entity and repository.
2. Add service-layer signup logic with password hashing.
3. Add Spring Security route rules.
4. Verification target:
   - Users can sign up and sign in; role-protected routes are enforced.

Example role enum:
`src/main/java/com/pathfinder/auth/domain/Role.java`

```java
package com.pathfinder.auth.domain;

public enum Role {
    JOB_SEEKER,
    MENTOR,
    ADMIN
}
```

Example user entity:
`src/main/java/com/pathfinder/auth/domain/UserAccount.java`

```java
package com.pathfinder.auth.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
```

Example repository:
`src/main/java/com/pathfinder/auth/repo/UserAccountRepository.java`

```java
package com.pathfinder.auth.repo;

import com.pathfinder.auth.domain.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

Example signup service:
`src/main/java/com/pathfinder/auth/service/AuthService.java`

```java
package com.pathfinder.auth.service;

import com.pathfinder.auth.domain.Role;
import com.pathfinder.auth.domain.UserAccount;
import com.pathfinder.auth.repo.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public void signup(String email, String rawPassword, Role role) {
        if (users.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        users.save(user);
    }
}
```

Example security config:
`src/main/java/com/pathfinder/auth/service/SecurityConfig.java`

```java
package com.pathfinder.auth.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/css/**", "/auth/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/mentor/**").hasAnyRole("MENTOR", "ADMIN")
                .requestMatchers("/seeker/**").hasAnyRole("JOB_SEEKER", "ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form.loginPage("/auth/login").permitAll())
            .logout(logout -> logout.logoutUrl("/auth/logout").logoutSuccessUrl("/"));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### Phase C: Core domain entities

1. Implement `SeekerProfile`, `MentorProfile`, `AvailabilitySlot`, `Session`, `ActionItem`, and `Payment`.
2. Define relationships and constraints.
3. Verification target:
   - Records for each MVP object can be created and retrieved.

Example session status enum:
`src/main/java/com/pathfinder/session/domain/SessionStatus.java`

```java
package com.pathfinder.session.domain;

public enum SessionStatus {
    REQUESTED,
    APPROVED,
    DECLINED,
    COMPLETED,
    CANCELLED
}
```

Example session entity:
`src/main/java/com/pathfinder/session/domain/MentorshipSession.java`

```java
package com.pathfinder.session.domain;

import com.pathfinder.auth.domain.UserAccount;
import jakarta.persistence.*;

@Entity
@Table(name = "sessions")
public class MentorshipSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "seeker_id")
    private UserAccount seeker;

    @ManyToOne(optional = false)
    @JoinColumn(name = "mentor_id")
    private UserAccount mentor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SessionStatus status = SessionStatus.REQUESTED;

    @Column(name = "booking_notes", length = 1500)
    private String bookingNotes;

    public Long getId() { return id; }
    public UserAccount getSeeker() { return seeker; }
    public void setSeeker(UserAccount seeker) { this.seeker = seeker; }
    public UserAccount getMentor() { return mentor; }
    public void setMentor(UserAccount mentor) { this.mentor = mentor; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public String getBookingNotes() { return bookingNotes; }
    public void setBookingNotes(String bookingNotes) { this.bookingNotes = bookingNotes; }
}
```

### Phase D: Workflow services

1. Implement transition rules for session lifecycle.
2. Reject invalid transitions.
3. Verification target:
   - Business rules are consistently enforced in service methods.

Example transition service:
`src/main/java/com/pathfinder/session/service/SessionService.java`

```java
package com.pathfinder.session.service;

import com.pathfinder.session.domain.MentorshipSession;
import com.pathfinder.session.domain.SessionStatus;
import com.pathfinder.session.repo.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    private final SessionRepository sessions;

    public SessionService(SessionRepository sessions) {
        this.sessions = sessions;
    }

    @Transactional
    public void transition(Long sessionId, SessionStatus target) {
        MentorshipSession session = sessions.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        SessionStatus current = session.getStatus();
        boolean valid =
            (current == SessionStatus.REQUESTED && (target == SessionStatus.APPROVED || target == SessionStatus.DECLINED)) ||
            (current == SessionStatus.APPROVED && (target == SessionStatus.COMPLETED || target == SessionStatus.CANCELLED));

        if (!valid) {
            throw new IllegalStateException("Invalid transition: " + current + " -> " + target);
        }

        session.setStatus(target);
    }
}
```

### Phase E: Controller and UI integration

1. Bind form inputs to DTOs.
2. Validate input and delegate to services.
3. Return user-friendly feedback to views.
4. Verification target:
   - Form submissions persist data and display expected results.

Example signup DTO:
`src/main/java/com/pathfinder/auth/dto/SignupRequest.java`

```java
package com.pathfinder.auth.dto;

import com.pathfinder.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SignupRequest {
    @Email
    @NotBlank
    private String email;

    @Size(min = 8, max = 100)
    private String password;

    private Role role;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
```

Example controller method:
`src/main/java/com/pathfinder/auth/web/AuthController.java`

```java
@PostMapping("/signup")
public String signup(
    @Valid @ModelAttribute("form") SignupRequest form,
    BindingResult bindingResult,
    RedirectAttributes redirectAttributes
) {
    if (bindingResult.hasErrors()) {
        return "layout";
    }
    authService.signup(form.getEmail(), form.getPassword(), form.getRole());
    redirectAttributes.addFlashAttribute("message", "Account created successfully");
    return "redirect:/auth/login";
}
```

### Phase F: Testing and stability

1. Add repository tests.
2. Add service tests.
3. Add integration tests for critical routes.
4. Verification target:
   - Core auth and session workflows are regression-safe.

Example repository test:
`src/test/java/com/pathfinder/auth/repo/UserAccountRepositoryTest.java`

```java
package com.pathfinder.auth.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.pathfinder.auth.domain.Role;
import com.pathfinder.auth.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class UserAccountRepositoryTest {

    @Autowired
    private UserAccountRepository repository;

    @Test
    void findByEmail_returnsRecordWhenPresent() {
        UserAccount user = new UserAccount();
        user.setEmail("test@example.com");
        user.setPasswordHash("hash");
        user.setRole(Role.JOB_SEEKER);
        repository.save(user);

        assertThat(repository.findByEmail("test@example.com")).isPresent();
    }
}
```
