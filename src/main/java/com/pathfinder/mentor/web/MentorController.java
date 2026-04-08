package com.pathfinder.mentor.web;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.web.AuthController;
import com.pathfinder.mentor.domain.VerificationStatus;
import com.pathfinder.mentor.service.MentorProfileService;
import com.pathfinder.mentor.domain.MentorProfile;
import com.pathfinder.session.domain.SessionRequest;
import com.pathfinder.session.domain.SessionStatus;
import com.pathfinder.session.service.SessionService;
import jakarta.servlet.http.HttpSession;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/mentor")
public class MentorController {

    private static final String MENTOR_NAVBAR = "fragments/navbar_mentor :: navbar";
    private static final Pattern TIME_24H_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final List<WeekdayOption> WEEKDAYS = List.of(
            new WeekdayOption("mon", "Monday"),
            new WeekdayOption("tue", "Tuesday"),
            new WeekdayOption("wed", "Wednesday"),
            new WeekdayOption("thu", "Thursday"),
            new WeekdayOption("fri", "Friday"),
            new WeekdayOption("sat", "Saturday"),
            new WeekdayOption("sun", "Sunday")
    );
    private final MentorProfileService mentorProfileService;
    private final SessionService sessionService;

    public MentorController(MentorProfileService mentorProfileService, SessionService sessionService) {
        this.mentorProfileService = mentorProfileService;
        this.sessionService = sessionService;
    }

    @GetMapping("/home")
    public String home(HttpSession session, Model model) {
        String mentorEmail = resolveCurrentMentorEmail(session, "");
        List<SessionRequest> mentorSessions = mentorEmail.isEmpty()
                ? List.of()
                : sessionService.getSessionsForMentor(mentorEmail);

        SessionRequest nextSession = mentorSessions.stream()
                .filter(request -> request.getStatus() == SessionStatus.APPROVED || request.getStatus() == SessionStatus.PAID)
                .max(Comparator.comparing(SessionRequest::getCreatedAt))
                .orElse(null);

        long pendingRequestCount = mentorSessions.stream()
                .filter(request -> request.getStatus() == SessionStatus.REQUESTED)
                .count();

        long sessionsThisMonth = mentorSessions.stream()
                .filter(request -> request.getCreatedAt() != null)
                .filter(request -> request.getCreatedAt().getMonth() == LocalDate.now().getMonth())
                .filter(request -> request.getStatus() != SessionStatus.DECLINED && request.getStatus() != SessionStatus.CANCELLED)
                .count();

        model.addAttribute("nextSession", nextSession);
        model.addAttribute("pendingRequestCount", pendingRequestCount);
        model.addAttribute("sessionsThisMonth", sessionsThisMonth);
        model.addAttribute("calendarDays", buildCalendarDays(mentorSessions));
        return renderPage(model, "Mentor home", "mentor/home :: content");
    }

    @GetMapping("/pay")
    public String pay(Model model) {
        return renderPage(model, "My pay", "mentor/pay :: content");
    }

    @GetMapping("/availability")
    public String availability(
            @RequestParam(defaultValue = "") String email,
            HttpSession session,
            Model model
    ) {
        String normalizedEmail = resolveCurrentMentorEmail(session, email);
        if (!normalizedEmail.isEmpty() && !model.containsAttribute("email")) {
            model.addAttribute("email", normalizedEmail);
        }
        List<AvailabilityRow> availabilityRows = defaultAvailabilityRows();
        if (!normalizedEmail.isEmpty()) {
            List<AvailabilityRow> savedRows = mergeAvailabilityRows(availabilityRows, mentorProfileService.findAvailabilityByEmail(normalizedEmail));
            availabilityRows = savedRows;
        }

        if (!model.containsAttribute("availabilityRows")) {
            model.addAttribute("availabilityRows", availabilityRows);
        }
        if (!model.containsAttribute("timezone")) {
            model.addAttribute("timezone", "America/Vancouver");
        }
        if (!model.containsAttribute("slotLengthMinutes")) {
            model.addAttribute("slotLengthMinutes", "45");
        }
        if (!model.containsAttribute("bufferMinutes")) {
            model.addAttribute("bufferMinutes", "10");
        }
        if (!model.containsAttribute("bookingNoticeHours")) {
            model.addAttribute("bookingNoticeHours", "24");
        }
        if (!model.containsAttribute("blockedDates")) {
            model.addAttribute("blockedDates", "");
        }
        if (!model.containsAttribute("previewSlots")) {
            model.addAttribute("previewSlots", buildPreviewSlots(availabilityRows));
        }
        return renderPage(model, "Mentor availability", "mentor/availability :: content");
    }

    @PostMapping("/availability")
    public String saveAvailability(
            @RequestParam MultiValueMap<String, String> formValues,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        List<AvailabilityRow> availabilityRows = buildRowsFromForm(formValues);
        String accountEmail = resolveCurrentMentorEmail(session, formValues.getFirst("email"));
        String timezone = normalizeText(formValues.getFirst("timezone"));
        if (timezone.isEmpty()) {
            timezone = "America/Vancouver";
        }
        String slotLengthMinutes = normalizeChoice(formValues.getFirst("slotLengthMinutes"), List.of("30", "45", "60"), "45");
        String bufferMinutes = normalizeChoice(formValues.getFirst("bufferMinutes"), List.of("0", "10", "15"), "10");
        String bookingNoticeHours = normalizeChoice(formValues.getFirst("bookingNoticeHours"), List.of("6", "12", "24", "48"), "24");
        String blockedDates = normalizeText(formValues.getFirst("blockedDates"));

        redirectAttributes.addFlashAttribute("availabilityRows", availabilityRows);
        redirectAttributes.addFlashAttribute("email", accountEmail);
        redirectAttributes.addFlashAttribute("timezone", timezone);
        redirectAttributes.addFlashAttribute("slotLengthMinutes", slotLengthMinutes);
        redirectAttributes.addFlashAttribute("bufferMinutes", bufferMinutes);
        redirectAttributes.addFlashAttribute("bookingNoticeHours", bookingNoticeHours);
        redirectAttributes.addFlashAttribute("blockedDates", blockedDates);
        redirectAttributes.addFlashAttribute("previewSlots", buildPreviewSlots(availabilityRows));

        long enabledDays = availabilityRows.stream().filter(AvailabilityRow::enabled).count();
        boolean invalidRange = availabilityRows.stream()
                .filter(AvailabilityRow::enabled)
                .anyMatch(row -> !isValidTimeRange(row.startTime(), row.endTime()));
        if (accountEmail.isEmpty()) {
            redirectAttributes.addFlashAttribute("formError", "Sign in with a mentor account first.");
            return "redirect:/mentor/availability";
        }
        if (enabledDays == 0) {
            redirectAttributes.addFlashAttribute("formError", "Select at least one available day.");
            return "redirect:/mentor/availability";
        }
        if (invalidRange) {
            redirectAttributes.addFlashAttribute("formError", "Each available day needs an end time after start time.");
            return "redirect:/mentor/availability";
        }

        try {
            mentorProfileService.replaceAvailability(accountEmail, availabilityRows.stream()
                    .filter(AvailabilityRow::enabled)
                    .map(row -> new MentorProfileService.AvailabilityInput(
                            weekdayToNumber(row.key()),
                            row.startTime(),
                            row.endTime()
                    ))
                    .toList());
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
            return "redirect:/mentor/availability";
        }

        redirectAttributes.addFlashAttribute("flashMessage", "Availability updated.");
        return "redirect:/mentor/availability";
    }

    @GetMapping("/profile")
    public String profile(
            @RequestParam(defaultValue = "") String email,
            HttpSession session,
            Model model
    ) {
        // Load the saved mentor profile and any admin status that should be shown with it.
        String normalizedEmail = resolveCurrentMentorEmail(session, email);
        if (!normalizedEmail.isEmpty() && !model.containsAttribute("email")) {
            model.addAttribute("email", normalizedEmail);
        }
        if (!normalizedEmail.isEmpty()) {
            populateProfileForm(model, normalizedEmail);
        }
        if (!model.containsAttribute("interviewCompanyBadges")) {
            model.addAttribute("interviewCompanyBadges", List.of());
        }
        return renderPage(model, "Mentor profile", "mentor/profile :: content");
    }

    @PostMapping("/profile")
    public String saveProfile(
            @RequestParam(defaultValue = "") String fullName,
            @RequestParam(defaultValue = "") String email,
            @RequestParam(defaultValue = "") String expertise,
            @RequestParam(defaultValue = "") String hourlyRate,
            @RequestParam(defaultValue = "") String currentTitle,
            @RequestParam(defaultValue = "") String currentCompany,
            @RequestParam(defaultValue = "") String interviewCompanies,
            @RequestParam(defaultValue = "") String bio,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String accountEmail = resolveCurrentMentorEmail(session, email);
        redirectAttributes.addFlashAttribute("fullName", fullName);
        redirectAttributes.addFlashAttribute("email", accountEmail);
        redirectAttributes.addFlashAttribute("expertise", expertise);
        redirectAttributes.addFlashAttribute("hourlyRate", hourlyRate);
        redirectAttributes.addFlashAttribute("currentTitle", currentTitle);
        redirectAttributes.addFlashAttribute("currentCompany", currentCompany);
        redirectAttributes.addFlashAttribute("interviewCompanies", interviewCompanies);
        redirectAttributes.addFlashAttribute("bio", bio);

        if (isBlank(fullName) || isBlank(accountEmail) || isBlank(expertise) || isBlank(hourlyRate)) {
            redirectAttributes.addFlashAttribute("formError", "Name, expertise, and hourly rate are required. Sign in first if needed.");
            return "redirect:/mentor/profile";
        }

        String titleValue = normalizeText(currentTitle);
        String currentCompanyBadge = normalizeText(currentCompany);
        String currentRoleBadge = buildRoleBadge(titleValue, currentCompanyBadge);
        List<String> interviewCompanyBadges = parseInterviewCompanyBadges(interviewCompanies);

        try {
            mentorProfileService.saveProfile(
                    accountEmail,
                    fullName,
                    expertise,
                    hourlyRate,
                    currentTitle,
                    currentCompany,
                    interviewCompanies,
                    bio
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
            return "redirect:/mentor/profile";
        }

        if (!currentRoleBadge.isEmpty()) {
            redirectAttributes.addFlashAttribute("currentRoleBadge", currentRoleBadge);
        }
        redirectAttributes.addFlashAttribute("interviewCompanyBadges", interviewCompanyBadges);
        redirectAttributes.addFlashAttribute("flashMessage", "Mentor profile saved.");
        return "redirect:/mentor/profile";
    }

    private void populateProfileForm(Model model, String email) {
        // Fill the profile form with the current saved mentor values.
        User mentorUser = mentorProfileService.findMentorUserByEmail(email);
        if (mentorUser != null && !model.containsAttribute("fullName")) {
            model.addAttribute("fullName", buildFullName(mentorUser.getFirstName(), mentorUser.getLastName()));
        }
        MentorProfile profile = mentorProfileService.findProfileByEmail(email);
        if (profile == null) {
            return;
        }
        List<String> skillBadges = mentorProfileService.findSkillsByEmail(email);
        List<String> interviewCompanyBadges = mentorProfileService.findInterviewCompaniesByEmail(email);

        if (!model.containsAttribute("expertise")) {
            model.addAttribute("expertise", profile.getExpertise());
        }
        if (!model.containsAttribute("hourlyRate")) {
            model.addAttribute("hourlyRate", formatCad(profile.getHourlyRateCents()));
        }
        if (!model.containsAttribute("currentCompany")) {
            model.addAttribute("currentCompany", profile.getCurrentCompany());
        }
        if (!model.containsAttribute("currentTitle")) {
            model.addAttribute("currentTitle", profile.getCurrentTitle());
        }
        if (!model.containsAttribute("interviewCompanies")) {
            model.addAttribute("interviewCompanies", String.join(", ", interviewCompanyBadges));
        }
        if (!model.containsAttribute("bio")) {
            model.addAttribute("bio", profile.getBio());
        }
        if (!model.containsAttribute("skillBadges")) {
            model.addAttribute("skillBadges", skillBadges);
        }
        if (!model.containsAttribute("interviewCompanyBadges")) {
            model.addAttribute("interviewCompanyBadges", interviewCompanyBadges);
        }
        if (!model.containsAttribute("currentRoleBadge")) {
            String currentRoleBadge = buildRoleBadge(
                    normalizeText(profile.getCurrentTitle()),
                    normalizeText(profile.getCurrentCompany())
            );
            if (!currentRoleBadge.isEmpty()) {
                model.addAttribute("currentRoleBadge", currentRoleBadge);
            }
        }
        // Show mentors the latest admin decision directly on their profile page.
        if (!model.containsAttribute("verificationStatusLabel")) {
            model.addAttribute("verificationStatusLabel", verificationStatusLabel(profile.getVerificationStatus()));
        }
        if (!model.containsAttribute("verificationStatusClass")) {
            model.addAttribute("verificationStatusClass", verificationStatusClass(profile.getVerificationStatus()));
        }
        if (!model.containsAttribute("verificationStatusMessage")) {
            model.addAttribute("verificationStatusMessage", verificationStatusMessage(profile.getVerificationStatus()));
        }
        if (!model.containsAttribute("adminNote")) {
            model.addAttribute("adminNote", normalizeText(profile.getAdminNote()));
        }
    }

    private String resolveCurrentMentorEmail(HttpSession session, String fallbackEmail) {
        String normalizedFallback = normalizeText(fallbackEmail);
        if (!normalizedFallback.isEmpty()) {
            return normalizedFallback;
        }
        Object sessionEmail = session.getAttribute(AuthController.SESSION_USER_EMAIL);
        Object sessionRole = session.getAttribute(AuthController.SESSION_USER_ROLE);
        if (sessionEmail == null || sessionRole == null) {
            return "";
        }
        if (!"mentor".equalsIgnoreCase(sessionRole.toString())) {
            return "";
        }
        return normalizeText(sessionEmail.toString());
    }

    private String buildFullName(String firstName, String lastName) {
        String combined = (normalizeText(firstName) + " " + normalizeText(lastName)).trim();
        return combined;
    }

    private List<String> parseInterviewCompanyBadges(String interviewCompanies) {
        if (isBlank(interviewCompanies)) {
            return List.of();
        }
        return Arrays.stream(interviewCompanies.split(","))
                .map(this::normalizeText)
                .filter(name -> !name.isEmpty())
                .distinct()
                .limit(8)
                .toList();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String buildRoleBadge(String title, String company) {
        if (title.isEmpty() && company.isEmpty()) {
            return "";
        }
        if (title.isEmpty()) {
            return company;
        }
        if (company.isEmpty()) {
            return title;
        }
        return title + " @ " + company;
    }

    private String verificationStatusLabel(VerificationStatus status) {
        if (status == null || status == VerificationStatus.PENDING) {
            return "Pending review";
        }
        if (status == VerificationStatus.APPROVED) {
            return "Approved";
        }
        return "Denied";
    }

    private String verificationStatusClass(VerificationStatus status) {
        if (status == null || status == VerificationStatus.PENDING) {
            return "statusBadge statusBadge--requested";
        }
        if (status == VerificationStatus.APPROVED) {
            return "statusBadge statusBadge--approved";
        }
        return "statusBadge statusBadge--declined";
    }

    private String verificationStatusMessage(VerificationStatus status) {
        if (status == null || status == VerificationStatus.PENDING) {
            return "Your profile is under review.";
        }
        if (status == VerificationStatus.APPROVED) {
            return "Your profile has been approved.";
        }
        return "Your profile was not approved.";
    }

    private List<CalendarDay> buildCalendarDays(List<SessionRequest> mentorSessions) {
        List<CalendarDay> calendarDays = new ArrayList<>();
        for (WeekdayOption weekday : WEEKDAYS.stream().filter(day -> !"sun".equals(day.key())).toList()) {
            List<CalendarEvent> events = mentorSessions.stream()
                    .filter(request -> matchesWeekday(request.getSlotTime(), weekday.label()))
                    .limit(3)
                    .map(request -> new CalendarEvent(
                            shortenSessionLabel(request.getSessionType(), request.getSlotTime()),
                            statusPillClass(request.getStatus())
                    ))
                    .toList();

            if (events.isEmpty()) {
                events = List.of(new CalendarEvent("No sessions planned", "calendarPill calendarPill--free"));
            }
            calendarDays.add(new CalendarDay(shortDayLabel(weekday.label()), events));
        }
        return calendarDays;
    }

    private boolean matchesWeekday(String slotTime, String weekdayLabel) {
        String normalizedSlot = normalizeText(slotTime).toLowerCase(Locale.ROOT);
        return normalizedSlot.contains(weekdayLabel.toLowerCase(Locale.ROOT));
    }

    private String shortDayLabel(String weekdayLabel) {
        return weekdayLabel.substring(0, 3).toUpperCase(Locale.ROOT);
    }

    private String shortenSessionLabel(String sessionType, String slotTime) {
        String type = normalizeText(sessionType);
        String slot = normalizeText(slotTime);
        if (slot.isEmpty()) {
            return type;
        }
        String timeRange = slot.contains("•")
                ? normalizeText(slot.substring(slot.lastIndexOf('•') + 1))
                : slot;
        int timezoneStart = timeRange.indexOf(" (");
        if (timezoneStart >= 0) {
            timeRange = normalizeText(timeRange.substring(0, timezoneStart));
        }
        return type + " • " + timeRange;
    }

    private String statusPillClass(SessionStatus status) {
        return switch (status) {
            case REQUESTED -> "calendarPill calendarPill--requested";
            case APPROVED, PAID -> "calendarPill calendarPill--approved";
            case COMPLETED -> "calendarPill calendarPill--done";
            case DECLINED, CANCELLED -> "calendarPill calendarPill--free";
        };
    }

    private String formatCad(Integer amountCents) {
        if (amountCents == null) {
            return "";
        }
        if (amountCents % 100 == 0) {
            return Integer.toString(amountCents / 100);
        }
        return String.format(Locale.ROOT, "%.2f", amountCents / 100.0);
    }

    private String renderPage(Model model, String title, String content) {
        model.addAttribute("title", title);
        model.addAttribute("navbarType", MENTOR_NAVBAR);
        model.addAttribute("content", content);
        return "layout";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private List<AvailabilityRow> defaultAvailabilityRows() {
        return List.of(
                new AvailabilityRow("mon", "Monday", true, "18:00", "20:00"),
                new AvailabilityRow("tue", "Tuesday", false, "18:00", "20:00"),
                new AvailabilityRow("wed", "Wednesday", true, "17:00", "19:00"),
                new AvailabilityRow("thu", "Thursday", true, "19:00", "20:00"),
                new AvailabilityRow("fri", "Friday", false, "17:00", "19:00"),
                new AvailabilityRow("sat", "Saturday", true, "10:00", "12:00"),
                new AvailabilityRow("sun", "Sunday", false, "10:00", "12:00")
        );
    }

    private List<AvailabilityRow> mergeAvailabilityRows(
            List<AvailabilityRow> baseRows,
            List<MentorProfileService.AvailabilityInput> savedAvailability
    ) {
        return baseRows.stream()
                .map(row -> {
                    MentorProfileService.AvailabilityInput match = savedAvailability.stream()
                            .filter(item -> item.weekday() == weekdayToNumber(row.key()))
                            .findFirst()
                            .orElse(null);
                    if (match == null) {
                        return row;
                    }
                    return new AvailabilityRow(row.key(), row.label(), true, match.startTime(), match.endTime());
                })
                .toList();
    }

    private List<AvailabilityRow> buildRowsFromForm(MultiValueMap<String, String> formValues) {
        List<AvailabilityRow> rows = new ArrayList<>();
        for (WeekdayOption weekday : WEEKDAYS) {
            boolean enabled = formValues.containsKey(weekday.key() + "Enabled");
            String startTime = normalizeTime(formValues.getFirst(weekday.key() + "Start"), "09:00");
            String endTime = normalizeTime(formValues.getFirst(weekday.key() + "End"), "17:00");
            rows.add(new AvailabilityRow(weekday.key(), weekday.label(), enabled, startTime, endTime));
        }
        return rows;
    }

    private List<String> buildPreviewSlots(List<AvailabilityRow> rows) {
        return rows.stream()
                .filter(AvailabilityRow::enabled)
                .map(row -> row.label() + " • " + formatTime(row.startTime()) + " - " + formatTime(row.endTime()))
                .limit(6)
                .toList();
    }

    private boolean isValidTimeRange(String startTime, String endTime) {
        return TIME_24H_PATTERN.matcher(startTime).matches()
                && TIME_24H_PATTERN.matcher(endTime).matches()
                && startTime.compareTo(endTime) < 0;
    }

    private String normalizeTime(String value, String fallback) {
        String normalized = normalizeText(value);
        if (TIME_24H_PATTERN.matcher(normalized).matches()) {
            return normalized;
        }
        return fallback;
    }

    private String normalizeChoice(String value, List<String> allowed, String fallback) {
        String normalized = normalizeText(value);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private String formatTime(String time24h) {
        if (!TIME_24H_PATTERN.matcher(time24h).matches()) {
            return time24h;
        }
        int hour = Integer.parseInt(time24h.substring(0, 2));
        String minute = time24h.substring(3, 5);
        String period = hour >= 12 ? "PM" : "AM";
        int convertedHour = hour % 12;
        if (convertedHour == 0) {
            convertedHour = 12;
        }
        return String.format(Locale.ROOT, "%d:%s %s", convertedHour, minute, period);
    }

    private int weekdayToNumber(String key) {
        return switch (key) {
            case "sun" -> 1;
            case "mon" -> 2;
            case "tue" -> 3;
            case "wed" -> 4;
            case "thu" -> 5;
            case "fri" -> 6;
            case "sat" -> 7;
            default -> throw new IllegalArgumentException("Unknown weekday key: " + key);
        };
    }

    private record WeekdayOption(String key, String label) {
    }

    private record AvailabilityRow(
            String key,
            String label,
            boolean enabled,
            String startTime,
            String endTime
    ) {
    }

    private record CalendarDay(
            String dayName,
            List<CalendarEvent> events
    ) {
    }

    private record CalendarEvent(
            String label,
            String cssClass
    ) {
    }
}
