# Plan: UI Improvements (Onboard, Delight, Adapt)

This plan outlines the UI/UX enhancements for PathFinder, focusing on onboarding, delightful interactions, and responsive adaptations.

## Objective
Improve the overall user experience by adding personality, better guidance for new users, and ensuring the interface works seamlessly across different devices.

## Key Files & Context
- `src/main/resources/static/css/app.css`: Central style sheet for all UI components.
- `src/main/resources/templates/fragments/navbar.html`: Main navigation.
- `src/main/resources/templates/landing/index.html`: First point of contact for users.
- `src/main/resources/templates/seeker/mentors.html`: Mentor discovery.
- `src/main/resources/templates/seeker/home.html`: Mentee dashboard.
- `src/main/resources/templates/mentor/home.html`: Mentor dashboard.

## Implementation Steps

### 1. Delight: Micro-interactions and Refined Styling
- **Button Animations**: Add a subtle lift on hover and a press effect for all `.btn` variants.
- **Tile Entry**: Implement a fade-in and slide-up animation for `.tile` elements to make the page load feel smoother.
- **Hover States**: Enhance hover states for `.tileLinkCard__link` and `.mentorCard__link` with subtle background shifts and scaling.
- **Status Badges**: Add more personality to status badges (e.g., subtle glow or distinct icons).

### 2. Adapt: Responsive Navigation and Grid Refinement
- **Responsive Navbar**:
    - Update `navbar.html` to include a mobile menu toggle (hamburger).
    - Add CSS to handle the navigation flow on smaller screens (collapsible menu).
- **Grid Optimizations**:
    - Adjust `.gridPage` to have better spacing and column behavior on tablet sizes.
    - Improve the `.weekCalendar` grid for mobile (stacking days if necessary or horizontal scroll).
- **Touch Targets**: Ensure all interactive elements have sufficient padding and size for touch interaction (building on existing 44px min-height).

### 3. Onboard: Enhanced Empty States and Guidance
- **Empty State Components**: Create a reusable "Empty State" pattern in CSS that includes:
    - An icon or placeholder illustration.
    - A clear heading and descriptive text.
    - A primary CTA to get the user started.
- **Apply Empty States**:
    - Update `seeker/mentors.html` with a more helpful empty state when no mentors match filters.
    - Add empty states to `seeker/home.html` and `mentor/home.html` for cases where no sessions or requests exist.
- **Landing Page Polish**: Add subtle entrance animations to the "How it works" steps to guide the user's eye.

## Verification & Testing
- **Visual Inspection**: Manually check all pages on different screen sizes (using browser DevTools and real devices if possible).
- **Interactive Testing**: Verify button and tile animations feel responsive and non-intrusive.
- **Empty State Flow**: Trigger empty states (e.g., by searching for a non-existent mentor) and verify the guidance is clear.
- **Responsive Navigation**: Ensure the hamburger menu works correctly on mobile and doesn't appear on desktop.
- **Accessibility**: Verify `aria-live` and `aria-label` are still working correctly with the new UI elements.
