package com.pathfinder.session.web;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SessionController {

    private static final String SEEKER_NAVBAR = "fragments/navbar_seeker :: navbar";
    private static final String MENTOR_NAVBAR = "fragments/navbar_mentor :: navbar";
    private static final DateTimeFormatter SUBMITTED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a", Locale.ENGLISH);
    private static final List<String> SESSION_TYPES = List.of(
            "Mock interview",
            "Resume review",
            "System design",
            "Career strategy"
    );

    private final DemoSessionStore sessionStore;

    public SessionController(DemoSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @GetMapping("/seeker/sessions/new")
    public String newSessionRequest(
            @RequestParam(defaultValue = "") String mentor,
            Model model
    ) {
        List<DemoSessionStore.MentorDirectoryItemView> mentors = sessionStore.getMentors();
        String requestedMentor = firstNonBlank((String) model.asMap().get("selectedMentor"), mentor);
        String selectedMentor = resolveMentorName(requestedMentor, mentors);
        DemoSessionStore.MentorDirectoryItemView selectedMentorInfo = sessionStore.getMentorByName(selectedMentor).orElse(null);
        List<DemoSessionStore.AvailabilitySlotView> availableSlots = sessionStore.getAvailabilityForMentor(selectedMentor);

        model.addAttribute("mentors", mentors);
        model.addAttribute("selectedMentor", selectedMentor);
        model.addAttribute("selectedMentorInfo", selectedMentorInfo);
        model.addAttribute("availableSlots", availableSlots);
        model.addAttribute("sessionTypes", SESSION_TYPES);

        if (!model.containsAttribute("selectedSlotId")) {
            model.addAttribute("selectedSlotId", "");
        }
        if (!model.containsAttribute("selectedSessionType")) {
            model.addAttribute("selectedSessionType", SESSION_TYPES.getFirst());
        }
        if (!model.containsAttribute("objective")) {
            model.addAttribute("objective", "");
        }
        if (!model.containsAttribute("bookingNotes")) {
            model.addAttribute("bookingNotes", "");
        }

        return renderSeekerPage(model, "Request session", "seeker/session_new :: content");
    }

    @PostMapping("/seeker/sessions")
    public String createSessionRequest(
            @RequestParam(defaultValue = "") String mentorName,
            @RequestParam(defaultValue = "") String slotId,
            @RequestParam(defaultValue = "") String sessionType,
            @RequestParam(defaultValue = "") String objective,
            @RequestParam(defaultValue = "") String bookingNotes,
            RedirectAttributes redirectAttributes
    ) {
        String normalizedMentor = safeTrim(mentorName);
        String normalizedSlotId = safeTrim(slotId);
        String normalizedSessionType = safeTrim(sessionType);
        String normalizedObjective = safeTrim(objective);
        String normalizedNotes = safeTrim(bookingNotes);

        if (isBlank(normalizedMentor) || isBlank(normalizedSlotId) || isBlank(normalizedSessionType) || isBlank(normalizedObjective)) {
            return redirectToSessionFormWithError(
                    redirectAttributes,
                    normalizedMentor,
                    normalizedSlotId,
                    normalizedSessionType,
                    normalizedObjective,
                    normalizedNotes,
                    "Mentor, availability slot, session type, and objective are required."
            );
        }

        if (sessionStore.getMentorByName(normalizedMentor).isEmpty()) {
            return redirectToSessionFormWithError(
                    redirectAttributes,
                    normalizedMentor,
                    normalizedSlotId,
                    normalizedSessionType,
                    normalizedObjective,
                    normalizedNotes,
                    "Select a valid mentor."
            );
        }

        if (!sessionStore.isSlotValidForMentor(normalizedMentor, normalizedSlotId)) {
            return redirectToSessionFormWithError(
                    redirectAttributes,
                    normalizedMentor,
                    normalizedSlotId,
                    normalizedSessionType,
                    normalizedObjective,
                    normalizedNotes,
                    "Select a valid availability slot for this mentor."
            );
        }

        DemoSessionStore.SessionRequestView request = sessionStore.createRequest(
                normalizedMentor,
                normalizedSlotId,
                normalizedSessionType,
                normalizedObjective,
                normalizedNotes
        );
        redirectAttributes.addFlashAttribute("flashMessage", "Session request submitted successfully.");
        return "redirect:/seeker/sessions/" + request.requestId();
    }

    @GetMapping("/seeker/sessions/{requestId}")
    public String sessionRequestDetail(
            @PathVariable String requestId,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        DemoSessionStore.SessionRequestView request = sessionStore.findRequest(requestId).orElse(null);
        if (request == null) {
            redirectAttributes.addFlashAttribute("formError", "Session request not found.");
            return "redirect:/seeker/mentors";
        }

        model.addAttribute("sessionRequest", request);
        model.addAttribute("submittedAtLabel", request.submittedAt().format(SUBMITTED_AT_FORMATTER));
        model.addAttribute("statusLabel", toStatusLabel(request.status()));
        model.addAttribute("statusClass", toStatusClass(request.status()));
        return renderSeekerPage(model, "Session request details", "seeker/session_detail :: content");
    }

    @GetMapping("/mentor/requests")
    public String mentorRequestQueue(Model model) {
        List<DemoSessionStore.SessionRequestView> requests = sessionStore.listRequestsForMentorQueue();
        List<DemoSessionStore.SessionRequestView> pendingRequests = requests.stream()
                .filter(request -> request.status() == DemoSessionStore.SessionStatus.REQUESTED)
                .toList();
        List<DemoSessionStore.SessionRequestView> previousRequests = requests.stream()
                .filter(request -> request.status() != DemoSessionStore.SessionStatus.REQUESTED)
                .toList();

        model.addAttribute("requests", requests);
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("previousRequests", previousRequests);
        return renderMentorPage(model, "Mentor requests", "mentor/requests :: content");
    }

    @PostMapping("/mentor/requests/{requestId}/decision")
    public String applyDecision(
            @PathVariable String requestId,
            @RequestParam(defaultValue = "") String decision,
            @RequestParam(defaultValue = "") String mentorNote,
            RedirectAttributes redirectAttributes
    ) {
        DemoSessionStore.SessionRequestView request = sessionStore.applyDecision(requestId, decision, mentorNote).orElse(null);
        if (request == null) {
            redirectAttributes.addFlashAttribute("formError", "Unable to update this request.");
            return "redirect:/mentor/requests";
        }

        redirectAttributes.addFlashAttribute(
                "flashMessage",
                "Request " + request.requestId() + " marked as " + toStatusLabel(request.status()).toLowerCase(Locale.ROOT) + "."
        );
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
        return "redirect:/seeker/sessions/new";
    }

    private String resolveMentorName(String mentorName, List<DemoSessionStore.MentorDirectoryItemView> mentors) {
        if (mentors.isEmpty()) {
            return "";
        }
        if (isBlank(mentorName)) {
            return mentors.getFirst().name();
        }
        return mentors.stream()
                .map(DemoSessionStore.MentorDirectoryItemView::name)
                .filter(name -> name.equalsIgnoreCase(mentorName.trim()))
                .findFirst()
                .orElse(mentors.getFirst().name());
    }

    private String toStatusLabel(DemoSessionStore.SessionStatus status) {
        return switch (status) {
            case REQUESTED -> "Requested";
            case APPROVED -> "Approved";
            case DECLINED -> "Declined";
        };
    }

    private String toStatusClass(DemoSessionStore.SessionStatus status) {
        return switch (status) {
            case REQUESTED -> "statusBadge statusBadge--requested";
            case APPROVED -> "statusBadge statusBadge--approved";
            case DECLINED -> "statusBadge statusBadge--declined";
        };
    }

    private String renderSeekerPage(Model model, String title, String content) {
        model.addAttribute("title", title);
        model.addAttribute("navbarType", SEEKER_NAVBAR);
        model.addAttribute("content", content);
        return "layout";
    }

    private String renderMentorPage(Model model, String title, String content) {
        model.addAttribute("title", title);
        model.addAttribute("navbarType", MENTOR_NAVBAR);
        model.addAttribute("content", content);
        return "layout";
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
