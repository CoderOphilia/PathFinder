# PathFinder

PathFinder is a Spring Boot + Thymeleaf web app for booking structured career mentorship sessions and tracking
progress (session summaries + action items).

## Current status

- The app is usable in demo mode.
- Most core screens and flows are in place.
- Admin moderation flows are now wired in for mentor review, user suspension/reactivation, and session oversight.
- Backend production work is still incomplete.
- Use unchecked backend items as the remaining implementation scope.

## MVP feature checklist (demo experience)

### Mentee

- [x] Create account (name, email, password, role)
- [x] Sign in with email and password
- [x] Forgot-password request flow (demo)
- [x] Use mentee dashboard and navigation
- [x] Browse mentor directory cards
- [x] Filter mentors by search text, industry, and interview company
- [x] Open public mentor profile pages
- [x] Start session request from mentor directory/profile
- [x] Select a mentor slot and submit objective + booking notes
- [x] View session request detail (status, payment state, timeline)
- [x] Cancel a request as a mentee when status allows it
- [x] Go to payment once a mentor approves the session
- [x] Save mentee profile fields and load them on future visits
- [x] Mentee session history page (all current + past requests)
- [x] Session action-item tracking (view, mark complete, reopen)

### Mentor

- [x] Use mentor dashboard and navigation
- [x] Save mentor profile (name, expertise, hourly rate, title, company, bio)
- [x] Save interview-company badges on profile
- [x] Save weekly availability (enabled days + start/end times)
- [x] Validate availability input (required day + valid time ranges)
- [x] Review pending request queue
- [x] Approve request with optional mentor note
- [x] Decline request with optional mentor note
- [x] Cancel approved session as mentor
- [x] Mark paid session as completed
- [ ] Add meeting link to approved sessions
- [x] Add post-session summary
- [ ] Add and manage post-session action items
- [x] Show mentor earnings and payout summary for completed paid sessions

### Admin

- [x] Use admin dashboard and navigation
- [x] View mentor review queue table
- [x] View mentor review detail panel
- [x] Save admin profile form
- [x] Approve mentor verification
- [x] Reject mentor verification / request profile updates
- [x] Suspend user accounts
- [x] Reactivate suspended accounts
- [x] Add a real admin session oversight view with actionable controls

### Session Management

- [x] Session statuses: `requested`, `approved`, `declined`, `cancelled`, `expired`, `completed`
- [x] Lock slots to prevent double-booking
- [x] Expire unreviewed requests automatically
- [x] Cancel approved-but-unpaid requests after due time
- [x] Structured pre-session booking notes (objective + optional notes)
- [x] Mentor decision workflow (approve/decline + note)
- [x] Completion workflow (paid session -> completed)
- [x] Apply 24-hour cancellation fee behavior
- [ ] Structured session summary object
- [ ] Structured session action-item object
- [ ] Session lifecycle audit/history view in UI

### Payments (MVP-level)

- [x] Session quote generation at request time (hourly/flat snapshot)
- [x] Payment-due timing on approved requests
- [x] Payment status tracking: `not started`, `paid`, `failed`, `partial refund`, `refunded`
- [x] Demo payment method selection and status update
- [x] Cancellation refund status handling
- [x] Real payment gateway integration
- [ ] Transaction IDs + provider webhook handling
- [ ] Transaction history page for mentees and mentors
- [ ] Mentor payout settlement workflow

## Backend work remaining (explicit scope)

These are the backend pieces still needed before shipping.

### Authentication and access control

- [x] Persist users with hashed passwords
- [ ] Add Spring Security configuration for route-level authorization
- [ ] Enforce role guards on all protected routes (mentee/mentor/admin)
- [ ] Add proper logout flow with session invalidation
- [ ] Add authorization tests for cross-role access attempts

### Profile data persistence

- [x] Persist mentor profile data
- [x] Persist mentor skills/interview-company tags
- [x] Persist mentor weekly availability rows
- [ ] Persist mentee profile fields (target role, goals, timezone, etc.)
- [ ] Persist admin profile fields (team, channel, notes, etc.)

### Mentor discovery backend

- [ ] Replace hardcoded mentor catalog with DB-backed mentor queries
- [ ] Source mentee mentor filters from persisted mentor data
- [ ] Keep public mentor profile pages DB-backed (not static/demo data)

### Session backend (durable state)

- [ ] Create persistent session-request entity/repository/service
- [ ] Persist slot reservations/locks with concurrency-safe booking
- [ ] Persist mentor decisions and notes
- [ ] Persist cancellation records and fee outcomes
- [ ] Persist completion records and completion timestamp
- [ ] Build mentee session-history query endpoints/services
- [ ] Build mentor request-history query endpoints/services

### Session output data

- [ ] Persist structured session summaries
- [ ] Persist structured action items per session
- [ ] Add mentee action-item status updates (open/done)
- [ ] Add mentor edit/update flow for summaries and action items

### Payments backend

- [ ] Replace demo payment submit with real provider integration
- [ ] Persist provider transaction IDs and payment metadata
- [ ] Add webhook handling with idempotency safeguards
- [ ] Persist payment/refund audit trail
- [ ] Build transaction history views for mentee and mentor
- [ ] Build payout calculation + payout-status backend flow

### Admin operations backend

- [ ] Persist mentor verification decisions (approve/reject/request changes)
- [x] Implement account suspension/reactivation backend actions
- [x] Build admin session oversight backend with actionable controls
- [ ] Add admin audit trail for moderation actions

### Data and platform readiness

- [ ] Add Flyway migrations for current schema
- [ ] Add MySQL runtime profile and validate schema compatibility
- [ ] Add seed/dev fixtures for reliable local testing
- [ ] Add integration tests for end-to-end critical flows

### Recommended build order

- [ ] Lock down auth and route-level authorization first
- [ ] Persist mentee/admin profiles and replace demo mentor catalog with DB queries
- [ ] Move session lifecycle from demo store to persistent entities/services
- [ ] Implement real payments + webhooks + transaction history
- [ ] Complete admin operations + audit trail
- [ ] Finish migrations, MySQL validation, and end-to-end integration tests

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
- Spring Data JPA
- H2 (file-backed local DB)
- Maven

### Planned Additions

- Spring Security (route-level access control)
- MySQL runtime profile
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
- `src/main/java/com/pathfinder/auth/` - authentication and session role routing
- `src/main/java/com/pathfinder/mentee/` - mentee pages + mentor discovery
- `src/main/java/com/pathfinder/mentor/` - mentor profile/availability/request queue
- `src/main/java/com/pathfinder/session/` - session request lifecycle + payment-state demo
- `src/main/java/com/pathfinder/admin/` - admin workspace and mentor review views

Within each feature:

- `web` - MVC controllers and form/request objects
- `service` - business logic
- `repo` - database repositories
- `domain` - entities/domain models
- `dto` - view/service DTOs

Views and assets:

- `src/main/resources/templates/landing/` - Thymeleaf pages for landing
- `src/main/resources/templates/{mentee,mentor,admin}/` - role-specific pages
- `src/main/resources/templates/fragments/` - shared layout fragments
- `src/main/resources/static/` - CSS/JS/images
- `src/main/resources/db/migration/` - Flyway migrations (planned)

## Roles

- `MENTEE`
- `MENTOR`
- `ADMIN`

## Team

- CEO/Lead: 300386351
- Member: 300398282
- Member: 300389976
- Member: 300388928
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

## License

Class project (CSIS 3275)
