package com.pathfinder.mentor.web;

import java.util.Arrays;
import java.util.ArrayList;
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

    @GetMapping("/home")
    public String home(Model model) {
        return renderPage(model, "Mentor home", "mentor/home :: content");
    }

    @GetMapping("/availability")
    public String availability(Model model) {
        if (!model.containsAttribute("availabilityRows")) {
            model.addAttribute("availabilityRows", defaultAvailabilityRows());
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
            model.addAttribute("previewSlots", buildPreviewSlots(defaultAvailabilityRows()));
        }
        return renderPage(model, "Mentor availability", "mentor/availability :: content");
    }

    @PostMapping("/availability")
    public String saveAvailability(
            @RequestParam MultiValueMap<String, String> formValues,
            RedirectAttributes redirectAttributes
    ) {
        List<AvailabilityRow> availabilityRows = buildRowsFromForm(formValues);
        String timezone = normalizeText(formValues.getFirst("timezone"));
        if (timezone.isEmpty()) {
            timezone = "America/Vancouver";
        }
        String slotLengthMinutes = normalizeChoice(formValues.getFirst("slotLengthMinutes"), List.of("30", "45", "60"), "45");
        String bufferMinutes = normalizeChoice(formValues.getFirst("bufferMinutes"), List.of("0", "10", "15"), "10");
        String bookingNoticeHours = normalizeChoice(formValues.getFirst("bookingNoticeHours"), List.of("6", "12", "24", "48"), "24");
        String blockedDates = normalizeText(formValues.getFirst("blockedDates"));

        redirectAttributes.addFlashAttribute("availabilityRows", availabilityRows);
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
        if (enabledDays == 0) {
            redirectAttributes.addFlashAttribute("formError", "Select at least one available day.");
            return "redirect:/mentor/availability";
        }
        if (invalidRange) {
            redirectAttributes.addFlashAttribute("formError", "Each available day needs an end time after start time.");
            return "redirect:/mentor/availability";
        }

        redirectAttributes.addFlashAttribute("flashMessage", "Availability updated (demo mode).");
        return "redirect:/mentor/availability";
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        if (!model.containsAttribute("interviewCompanyBadges")) {
            model.addAttribute("interviewCompanyBadges", List.of());
        }
        return renderPage(model, "Mentor profile", "mentor/profile :: content");
    }

    @PostMapping("/profile")
    public String saveProfile(
            @RequestParam(defaultValue = "") String fullName,
            @RequestParam(defaultValue = "") String expertise,
            @RequestParam(defaultValue = "") String hourlyRate,
            @RequestParam(defaultValue = "") String currentTitle,
            @RequestParam(defaultValue = "") String currentCompany,
            @RequestParam(defaultValue = "") String interviewCompanies,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(fullName) || isBlank(expertise) || isBlank(hourlyRate)) {
            redirectAttributes.addFlashAttribute("formError", "Name, expertise, and hourly rate are required.");
            return "redirect:/mentor/profile";
        }

        String titleValue = normalizeText(currentTitle);
        String currentCompanyBadge = normalizeText(currentCompany);
        String currentRoleBadge = buildRoleBadge(titleValue, currentCompanyBadge);
        List<String> interviewCompanyBadges = parseInterviewCompanyBadges(interviewCompanies);
        if (!currentRoleBadge.isEmpty()) {
            redirectAttributes.addFlashAttribute("currentRoleBadge", currentRoleBadge);
        }
        redirectAttributes.addFlashAttribute("interviewCompanyBadges", interviewCompanyBadges);
        redirectAttributes.addFlashAttribute("flashMessage", "Mentor profile saved (demo mode).");
        return "redirect:/mentor/profile";
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
}
