# PathFinder

PathFinder is a Spring Boot + Thymeleaf web app for booking structured career mentorship sessions and tracking
progress (session summaries + action items).
## How to Access Project

To switch to a specific branch in the repository, follow these steps:

1. **Clone the Repository** (if you haven't already):

   ```bash
   git clone https://github.com/CoderOphilia/PathFinder.git
   ```
   &emsp; or

   ```bash
   gh repo clone CoderOphilia/PathFinder/.git
   ```

2. **Navigate to the Project Directory**:

   ```bash
   cd PathFinder
   ```
3. **Create a new Branch and switch to the branch**
    ```bash
   git checkout -b <branch-name/feature-name>
   ```

### Steps to Work on Project Locally
1. **Pull Latest Changes from dev branch**
   - Fetch the latest updates from the `dev` branch:
     ```bash
     git fetch origin dev
     ```
   - Merge the latest changes from `dev` into your branch:
     ```bash
     git merge origin/dev
     ```

2. **Create and Switch to Your Branch**
     ```bash
     git checkout -b <branch-name/feature-name>
     ```


3. **Resolve Any Conflicts**
   - If there are any merge conflicts, resolve them in your local environment and commit the changes.

4. **Push Updated Branch**
   - After ensuring everything is working, push your updated branch:
     ```bash
     git push origin <branch-name/feature-name>
     ```


## Current Status

- Running app: Spring Boot + Thymeleaf with a basic landing page at `/`.
- Not implemented yet: authentication/roles, database, session flows, payments.

## Planned MVP Scope

### Job Seeker

- Sign up / log in
- Create and manage a career profile
- Browse mentors and view mentor profiles
- Request a session (with booking notes/objectives)
- View session status and session history
- View action items from sessions and mark them complete

### Mentor

- Sign up / log in
- Create and manage mentor profile (expertise, pricing)
- Manage availability slots
- Approve or decline session requests
- Add session summary and action items
- View earnings summary (based on completed paid sessions)

### Admin

- Verify mentors (approve / reject)
- Suspend / reactivate accounts
- Oversee sessions and basic platform activity

### Session Management

- Session lifecycle: `requested` -> `approved` / `declined` -> `completed` / `cancelled`
- Structured booking notes (pre-session)
- Structured summary + action items (post-session)

### Payments (MVP-level)

- Session pricing
- Payment status tracking: `unpaid` / `paid` / `refunded`
- Basic transaction history

## Non-Goals (Not in MVP)

- In-app messaging/chat
- Built-in video calling
- AI matching/recommendations
- Job board / employer portal

## Session Calls (How sessions happen)

Sessions happen via an external meeting link (Google Meet/Zoom) stored on the session.
MVP approach: mentor adds the meeting link after approving the session.

## Tech Stack

### Current

- Java 21 (project target)
- Spring Boot 3.4.1 (MVC)
- Thymeleaf (server-rendered views)
- Maven

### Planned Additions

- Spring Security (role-based access)
- Spring Data JPA + MySQL
- Flyway (DB migrations)

## Getting Started

### Prerequisites

- JDK 21+ (you can use a newer JDK locally; the project targets Java 21)
- Maven 3.9+

### Run (Dev)

```bash
mvn spring-boot:run
```

Then open `http://localhost:8080`.

### Build

```bash
mvn package
```

## Project Structure

Java code is organized by feature. If you are working on a feature, start in that feature folder:

- `src/main/java/com/pathfinder/landing/web/` - landing page controller(s)
- `src/main/java/com/pathfinder/auth/` - authentication/roles (planned)
- `src/main/java/com/pathfinder/seeker/` - jobseeker feature (planned)
- `src/main/java/com/pathfinder/mentor/` - mentor feature (planned)
- `src/main/java/com/pathfinder/session/` - sessions/action items/payments (planned)
- `src/main/java/com/pathfinder/admin/` - admin tools (planned)

Within each feature:

- `web` - MVC controllers and form/request objects
- `service` - business logic
- `repo` - database repositories
- `domain` - entities/domain models
- `dto` - view/service DTOs

Views and assets:

- `src/main/resources/templates/landing/` - Thymeleaf pages for landing
- `src/main/resources/templates/{seeker,mentor,admin}/` - planned pages by role
- `src/main/resources/templates/fragments/` - shared layout fragments
- `src/main/resources/static/` - CSS/JS/images
- `src/main/resources/db/migration/` - Flyway migrations (planned)

## Roles (Planned)

- `JOB_SEEKER`
- `MENTOR`
- `ADMIN`

## Team

- CEO/Lead: 300386351
- Member: 300398282
- Member: 300389976
- Member: 300388928

## License

Class project (CSIS 3275)
