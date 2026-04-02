package com.pathfinder.session.web;

import com.pathfinder.auth.web.AuthController;
import com.pathfinder.mentor.service.MentorProfileService;
import com.pathfinder.session.domain.SessionRequest;
import com.pathfinder.session.domain.SessionStatus;
import com.pathfinder.session.service.SessionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.Comparator;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Controller
public class SessionController {

    private static final String MENTEE_NAVBAR = "fragments/navbar_mentee :: navbar";
    private static final String MENTOR_NAVBAR = "fragments/navbar_mentor :: navbar";
    private static final DateTimeFormatter CREATED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a", Locale.ENGLISH);
    private static final List<String> SESSION_TYPES = List.of(
            "Mock interview",
            "Resume review",
            "System design",
            "Career strategy"
    );

    private final MentorProfileService mentorProfileService;
    private final SessionService sessionService;

    public SessionController(MentorProfileService mentorProfileService, SessionService sessionService) {
        this.mentorProfileService = mentorProfileService;
        this.sessionService = sessionService;
    }

    @GetMapping({"/seeker/sessions/new", "/mentee/sessions/new"})
    public String newSessionRequest(
            @RequestParam(defaultValue = "") String mentor,
            Model model
    ) {
        List<MentorDirectoryItemView> mentors = mentorProfileService.listPublicMentors().stream()
                .map(profile -> new MentorDirectoryItemView(profile.name(), profile.rate(), profile.tagline()))
                .sorted(Comparator.comparing(MentorDirectoryItemView::name))
                .toList();
        String selectedMentor = resolveMentorName(mentor, mentors);
        MentorDirectoryItemView selectedMentorInfo = mentors.stream()
                .filter(item -> item.name().equalsIgnoreCase(selectedMentor))
                .findFirst()
                .orElse(null);
        List<AvailabilitySlotView> availableSlots = buildAvailabilitySlots(selectedMentor);

        model.addAttribute("mentors", mentors);
        model.addAttribute("selectedMentor", selectedMentor);
        model.addAttribute("selectedMentorInfo", selectedMentorInfo);
        model.addAttribute("availableSlots", availableSlots);
        model.addAttribute("sessionTypes", SESSION_TYPES);
        model.addAttribute("selectedSlotId", availableSlots.isEmpty() ? "" : availableSlots.getFirst().slotId());

        if (!model.containsAttribute("selectedSessionType")) {
            model.addAttribute("selectedSessionType", SESSION_TYPES.getFirst());
        }
        if (!model.containsAttribute("objective")) {
            model.addAttribute("objective", "");
        }
        if (!model.containsAttribute("bookingNotes")) {
            model.addAttribute("bookingNotes", "");
        }

        model.addAttribute("selectedQuoteLabel", "");
        model.addAttribute("selectedPricingLabel", selectedMentorInfo == null ? "" : "Mentor profile pricing");
        return renderMenteePage(model, "Request session", "mentee/session_new :: content");
    }

    @PostMapping({"/seeker/sessions", "/mentee/sessions"})
    public String createSessionRequest(
            @RequestParam(defaultValue = "") String mentorName,
            @RequestParam(defaultValue = "") String slotId,
            @RequestParam(defaultValue = "") String sessionType,
            @RequestParam(defaultValue = "") String objective,
            @RequestParam(defaultValue = "") String bookingNotes,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String menteeEmail = currentSessionEmail(session);
        if (isBlank(menteeEmail)) {
            return redirectToSessionFormWithError(
                    redirectAttributes, mentorName, slotId, sessionType, objective, bookingNotes,
                    "Sign in as a mentee before requesting a session."
            );
        }

        AvailabilitySlotView slot = buildAvailabilitySlots(mentorName).stream()
                .filter(item -> item.slotId().equalsIgnoreCase(slotId))
                .findFirst()
                .orElse(null);
        if (slot == null) {
            return redirectToSessionFormWithError(
                    redirectAttributes, mentorName, slotId, sessionType, objective, bookingNotes,
                    "Select a valid mentor slot."
            );
        }

        try {
            String mentorEmail = mentorProfileService.findMentorEmailByName(mentorName);
            SessionRequest request = sessionService.createSession(
                    menteeEmail,
                    mentorEmail,
                    mentorName,
                    slot.displayLabel(),
                    sessionType,
                    objective,
                    bookingNotes
            );
            redirectAttributes.addFlashAttribute("flashMessage", "Session request submitted successfully.");
            return "redirect:/mentee/sessions/" + request.getId();
        } catch (IllegalArgumentException exception) {
            return redirectToSessionFormWithError(
                    redirectAttributes, mentorName, slotId, sessionType, objective, bookingNotes,
                    exception.getMessage()
            );
        }
    }

    @GetMapping({"/seeker/sessions/{requestId}", "/mentee/sessions/{requestId}"})
    public String sessionRequestDetail(
            @PathVariable Long requestId,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        SessionRequest request = sessionService.getSessionById(requestId);
        if (request == null) {
            redirectAttributes.addFlashAttribute("formError", "Session request not found.");
            return "redirect:/mentee/mentors";
        }

        model.addAttribute("sessionRequest", request);
        model.addAttribute("submittedAtLabel", request.getCreatedAt().format(CREATED_AT_FORMATTER));
        model.addAttribute("statusLabel", toStatusLabel(request.getStatus()));
        model.addAttribute("statusClass", toStatusClass(request.getStatus()));
        model.addAttribute("paymentStatusLabel", toPaymentStatusLabel(request.isPaymentCompleted()));
        model.addAttribute("paymentStatusClass", toPaymentStatusClass(request.isPaymentCompleted()));
        model.addAttribute("quotedAmountLabel", "Estimated payment");
        model.addAttribute("pricingModelLabel", "Mentor pricing");
        model.addAttribute("paymentDueLabel", "");
        model.addAttribute("canPay", request.getStatus() == SessionStatus.APPROVED);
        model.addAttribute("canCancelAsMentee", canCancelAsMentee(request.getStatus()));
        model.addAttribute("cancellationLabel", "");
        return renderMenteePage(model, "Session request details", "mentee/session_detail :: content");
    }

    @GetMapping({"/seeker/sessions/{requestId}/payment", "/mentee/sessions/{requestId}/payment"})
    public String paymentPage(
            @PathVariable Long requestId,
            @RequestParam(defaultValue = "false") boolean preview,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        SessionRequest request = sessionService.getSessionById(requestId);
        if (request == null) {
            redirectAttributes.addFlashAttribute("formError", "Session request not found.");
            return "redirect:/mentee/mentors";
        }

        if (request.getStatus() != SessionStatus.APPROVED && !preview) {
            redirectAttributes.addFlashAttribute("formError", "Payment is available only after mentor approval.");
            return "redirect:/mentee/sessions/" + requestId;
        }

        if (!model.containsAttribute("paymentMethod")) {
            model.addAttribute("paymentMethod", "");
        }
        model.addAttribute("previewMode", preview);
        model.addAttribute("sessionRequest", request);
        model.addAttribute("quotedAmountLabel", "Estimated payment");
        model.addAttribute("paymentDueLabel", request.getCreatedAt().format(CREATED_AT_FORMATTER));
        return renderMenteePage(model, "Session payment", "mentee/session_payment :: content");
    }

    @PostMapping({"/seeker/sessions/{requestId}/payment", "/mentee/sessions/{requestId}/payment"})
    public String submitPayment(
            @PathVariable Long requestId,
            @RequestParam(defaultValue = "") String paymentMethod,
        RedirectAttributes redirectAttributes
    ) {
        if (isBlank(paymentMethod)) {
            redirectAttributes.addFlashAttribute("formError", "Choose a payment method.");
            redirectAttributes.addFlashAttribute("paymentMethod", paymentMethod);
            return "redirect:/mentee/sessions/" + requestId + "/payment";
        }

        try {
            sessionService.paySession(requestId);
            redirectAttributes.addFlashAttribute("flashMessage", "Payment recorded successfully.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
        }
        return "redirect:/mentee/sessions/" + requestId;
    }

    @PostMapping({"/seeker/sessions/{requestId}/payment/preview-complete", "/mentee/sessions/{requestId}/payment/preview-complete"})
    public String previewPaymentCompletion(
            @PathVariable Long requestId,
            RedirectAttributes redirectAttributes
    ) {
        if (sessionService.getSessionById(requestId) == null) {
            redirectAttributes.addFlashAttribute("formError", "Session request not found.");
            return "redirect:/mentee/mentors";
        }
        redirectAttributes.addFlashAttribute("flashMessage", "Payment completion flow previewed (no changes made).");
        return "redirect:/mentee/sessions/" + requestId;
    }

    @PostMapping({"/seeker/sessions/{requestId}/cancel", "/mentee/sessions/{requestId}/cancel"})
    public String cancelAsMentee(
            @PathVariable Long requestId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            sessionService.cancelSession(requestId);
            redirectAttributes.addFlashAttribute("flashMessage", "Session cancelled.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
        }
        return "redirect:/mentee/sessions/" + requestId;
    }

    @GetMapping("/mentor/requests")
    public String mentorRequestQueue(HttpSession session, Model model) {
        String mentorEmail = currentSessionEmail(session);
        List<SessionRequest> requests = (isBlank(mentorEmail)
                ? List.<SessionRequest>of()
                : sessionService.getSessionsForMentor(mentorEmail));
        List<SessionRequest> pendingRequests = requests.stream()
                .filter(request -> request.getStatus() == SessionStatus.REQUESTED)
                .toList();
        List<SessionRequest> previousRequests = requests.stream()
                .filter(request -> request.getStatus() != SessionStatus.REQUESTED)
                .toList();

        if (isBlank(mentorEmail)) {
            model.addAttribute("formError", "Sign in as a mentor to view your request queue.");
        }
        model.addAttribute("requests", requests);
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("previousRequests", previousRequests);
        return renderMentorPage(model, "Mentor requests", "mentor/requests :: content");
    }

    @PostMapping("/mentor/requests/{requestId}/decision")
    public String applyDecision(
            @PathVariable Long requestId,
            @RequestParam(defaultValue = "") String decision,
            @RequestParam(defaultValue = "") String mentorNote,
            RedirectAttributes redirectAttributes
    ) {
        try {
            if ("approve".equalsIgnoreCase(decision)) {
                sessionService.approveSession(requestId, mentorNote);
                redirectAttributes.addFlashAttribute("flashMessage", "Session approved.");
            } else if ("decline".equalsIgnoreCase(decision)) {
                sessionService.declineSession(requestId, mentorNote);
                redirectAttributes.addFlashAttribute("flashMessage", "Session declined.");
            } else {
                redirectAttributes.addFlashAttribute("formError", "Choose approve or decline.");
            }
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
        }
        return "redirect:/mentor/requests";
    }

    @PostMapping("/mentor/sessions/{requestId}/cancel")
    public String cancelAsMentor(
            @PathVariable Long requestId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            sessionService.cancelSession(requestId);
            redirectAttributes.addFlashAttribute("flashMessage", "Session cancelled.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
        }
        return "redirect:/mentor/requests";
    }

    @PostMapping("/mentor/sessions/{requestId}/complete")
    public String completeSession(
            @PathVariable Long requestId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            sessionService.completeSession(requestId);
            redirectAttributes.addFlashAttribute("flashMessage", "Session completed.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("formError", exception.getMessage());
        }
        return "redirect:/mentor/requests";
    }

    private String redirectToSessionFormWithError(
            RedirectAttributes redirectAttributes,
            String mentorName,
            String slotId,
            String sessionType,
            String objective,
            String bookingNotes,
            String errorMessage
    ) {
        redirectAttributes.addFlashAttribute("formError", errorMessage);
        redirectAttributes.addFlashAttribute("selectedMentor", mentorName);
        redirectAttributes.addFlashAttribute("selectedSlotId", slotId);
        redirectAttributes.addFlashAttribute("selectedSessionType", sessionType);
        redirectAttributes.addFlashAttribute("objective", objective);
        redirectAttributes.addFlashAttribute("bookingNotes", bookingNotes);
        if (!isBlank(mentorName)) {
            redirectAttributes.addAttribute("mentor", mentorName);
        }
        return "redirect:/mentee/sessions/new";
    }

    private String resolveMentorName(String mentorName, List<MentorDirectoryItemView> mentors) {
        if (mentors.isEmpty()) {
            return "";
        }
        if (isBlank(mentorName)) {
            return mentors.getFirst().name();
        }
        return mentors.stream()
                .map(MentorDirectoryItemView::name)
                .filter(name -> name.equalsIgnoreCase(mentorName.trim()))
                .findFirst()
                .orElse(mentors.getFirst().name());
    }

    private List<AvailabilitySlotView> buildAvailabilitySlots(String mentorName) {
        String timezone = "America/Vancouver";
        return mentorProfileService.findAvailabilityByMentorName(mentorName).stream()
                .map(item -> new AvailabilitySlotView(
                        slotIdFor(item),
                        weekdayLabel(item.weekday()),
                        timeRange(item.startTime(), item.endTime()),
                        timezone
                ))
                .toList();
    }

    private String slotIdFor(MentorProfileService.AvailabilityInput item) {
        return "slot-" + item.weekday() + "-" + item.startTime().replace(":", "") + "-" + item.endTime().replace(":", "");
    }

    private String weekdayLabel(int weekday) {
        return switch (weekday) {
            case 1 -> "Sunday";
            case 2 -> "Monday";
            case 3 -> "Tuesday";
            case 4 -> "Wednesday";
            case 5 -> "Thursday";
            case 6 -> "Friday";
            case 7 -> "Saturday";
            default -> "Day";
        };
    }

    private String timeRange(String startTime, String endTime) {
        return formatTime(startTime) + " - " + formatTime(endTime);
    }

    private String formatTime(String time24h) {
        if (isBlank(time24h) || !time24h.contains(":")) {
            return time24h;
        }
        int hour = Integer.parseInt(time24h.substring(0, 2));
        String minute = time24h.substring(3, 5);
        String period = hour >= 12 ? "PM" : "AM";
        int convertedHour = hour % 12;
        if (convertedHour == 0) {
            convertedHour = 12;
        }
        return convertedHour + ":" + minute + " " + period;
    }

    private boolean canCancelAsMentee(SessionStatus status) {
        return status == SessionStatus.REQUESTED
                || status == SessionStatus.APPROVED
                || status == SessionStatus.PAID;
    }

    private String toStatusLabel(SessionStatus status) {
        return switch (status) {
            case REQUESTED -> "Requested";
            case APPROVED -> "Approved - payment pending";
            case PAID -> "Approved - paid";
            case DECLINED -> "Declined";
            case CANCELLED -> "Cancelled";
            case COMPLETED -> "Completed";
        };
    }

    private String toStatusClass(SessionStatus status) {
        return switch (status) {
            case REQUESTED -> "statusBadge statusBadge--requested";
            case APPROVED -> "statusBadge statusBadge--pendingPayment";
            case PAID -> "statusBadge statusBadge--approved";
            case DECLINED -> "statusBadge statusBadge--declined";
            case CANCELLED -> "statusBadge statusBadge--cancelled";
            case COMPLETED -> "statusBadge statusBadge--completed";
        };
    }

    private String toPaymentStatusLabel(boolean paymentCompleted) {
        return paymentCompleted ? "Paid" : "Not started";
    }

    private String toPaymentStatusClass(boolean paymentCompleted) {
        return paymentCompleted ? "statusBadge statusBadge--approved" : "statusBadge statusBadge--neutral";
    }

    private String renderMenteePage(Model model, String title, String content) {
        model.addAttribute("title", title);
        model.addAttribute("navbarType", MENTEE_NAVBAR);
        model.addAttribute("content", content);
        return "layout";
    }

    private String renderMentorPage(Model model, String title, String content) {
        model.addAttribute("title", title);
        model.addAttribute("navbarType", MENTOR_NAVBAR);
        model.addAttribute("content", content);
        return "layout";
    }

    private String currentSessionEmail(HttpSession session) {
        Object email = session.getAttribute(AuthController.SESSION_USER_EMAIL);
        return email == null ? "" : email.toString().trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record MentorDirectoryItemView(
            String name,
            String rate,
            String tagline
    ) {
    }

    private record AvailabilitySlotView(
            String slotId,
            String weekday,
            String timeRange,
            String timezone
    ) {
        private String displayLabel() {
            return weekday + " • " + timeRange + " (" + timezone + ")";
        }
    }
}
