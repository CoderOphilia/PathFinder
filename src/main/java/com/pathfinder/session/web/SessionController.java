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
    private static final DateTimeFormatter PAYMENT_DUE_FORMATTER =
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

        String selectedSlotId = firstNonBlank((String) model.asMap().get("selectedSlotId"), "");
        if (isBlank(selectedSlotId) && !availableSlots.isEmpty()) {
            selectedSlotId = availableSlots.getFirst().slotId();
        }
        String selectedSlotIdSnapshot = selectedSlotId;
        if (!availableSlots.stream().anyMatch(slot -> slot.slotId().equalsIgnoreCase(selectedSlotIdSnapshot))) {
            selectedSlotId = availableSlots.isEmpty() ? "" : availableSlots.getFirst().slotId();
        }
        model.addAttribute("selectedSlotId", selectedSlotId);

        if (!model.containsAttribute("selectedSessionType")) {
            model.addAttribute("selectedSessionType", SESSION_TYPES.getFirst());
        }
        if (!model.containsAttribute("objective")) {
            model.addAttribute("objective", "");
        }
        if (!model.containsAttribute("bookingNotes")) {
            model.addAttribute("bookingNotes", "");
        }

        model.addAttribute("selectedQuoteLabel", quotePreviewLabel(selectedMentorInfo, selectedSlotId));
        model.addAttribute("selectedPricingLabel", pricingModelLabel(selectedMentorInfo));

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

        if (!sessionStore.isSlotAvailableForMentor(normalizedMentor, normalizedSlotId)) {
            return redirectToSessionFormWithError(
                    redirectAttributes,
                    normalizedMentor,
                    normalizedSlotId,
                    normalizedSessionType,
                    normalizedObjective,
                    normalizedNotes,
                    "That slot was just reserved. Choose another available time."
            );
        }

        DemoSessionStore.SessionRequestView request;
        try {
            request = sessionStore.createRequest(
                    normalizedMentor,
                    normalizedSlotId,
                    normalizedSessionType,
                    normalizedObjective,
                    normalizedNotes
            );
        } catch (IllegalStateException exception) {
            return redirectToSessionFormWithError(
                    redirectAttributes,
                    normalizedMentor,
                    normalizedSlotId,
                    normalizedSessionType,
                    normalizedObjective,
                    normalizedNotes,
                    "That slot is no longer available."
            );
        }

        redirectAttributes.addFlashAttribute(
                "flashMessage",
                "Session request submitted and slot reserved for 24 hours."
        );
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
        model.addAttribute("paymentStatusLabel", toPaymentStatusLabel(request.paymentStatus()));
        model.addAttribute("paymentStatusClass", toPaymentStatusClass(request.paymentStatus()));
        model.addAttribute("quotedAmountLabel", sessionStore.formatAmount(request.quotedAmountCents()));
        model.addAttribute("pricingModelLabel", request.pricingModelSnapshot() == DemoSessionStore.PricingModel.HOURLY ? "Hourly" : "Flat");
        model.addAttribute("paymentDueLabel", request.paymentDueAt() == null ? "" : request.paymentDueAt().format(PAYMENT_DUE_FORMATTER));
        model.addAttribute("canPay", request.status() == DemoSessionStore.SessionStatus.APPROVED_PENDING_PAYMENT);
        model.addAttribute("canCancelAsMentee", canCancelAsMentee(request.status()));
        model.addAttribute("cancellationLabel", cancellationLabel(request));
        return renderSeekerPage(model, "Session request details", "seeker/session_detail :: content");
    }

    @GetMapping("/seeker/sessions/{requestId}/payment")
    public String paymentPage(
            @PathVariable String requestId,
            @RequestParam(defaultValue = "false") boolean preview,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        DemoSessionStore.SessionRequestView request = sessionStore.findRequest(requestId).orElse(null);
        if (request == null) {
            redirectAttributes.addFlashAttribute("formError", "Session request not found.");
            return "redirect:/seeker/mentors";
        }

        boolean payable = request.status() == DemoSessionStore.SessionStatus.APPROVED_PENDING_PAYMENT;
        if (request.status() == DemoSessionStore.SessionStatus.APPROVED_PAID && !preview) {
            redirectAttributes.addFlashAttribute("flashMessage", "This session has already been paid.");
            return "redirect:/seeker/sessions/" + request.requestId();
        }

        if (!payable && !preview) {
            redirectAttributes.addFlashAttribute("formError", "Payment is available only after mentor approval.");
            return "redirect:/seeker/sessions/" + request.requestId();
        }

        if (!model.containsAttribute("paymentMethod")) {
            model.addAttribute("paymentMethod", "");
        }
        model.addAttribute("previewMode", preview);
        model.addAttribute("sessionRequest", request);
        model.addAttribute("quotedAmountLabel", sessionStore.formatAmount(request.quotedAmountCents()));
        model.addAttribute(
                "paymentDueLabel",
                request.paymentDueAt() == null ? request.slotStartAt().format(PAYMENT_DUE_FORMATTER) : request.paymentDueAt().format(PAYMENT_DUE_FORMATTER)
        );
        return renderSeekerPage(model, "Session payment", "seeker/session_payment :: content");
    }

    @PostMapping("/seeker/sessions/{requestId}/payment")
    public String submitPayment(
            @PathVariable String requestId,
            @RequestParam(defaultValue = "") String paymentMethod,
            RedirectAttributes redirectAttributes
    ) {
        String normalizedPaymentMethod = safeTrim(paymentMethod);
        if (isBlank(normalizedPaymentMethod)) {
            redirectAttributes.addFlashAttribute("formError", "Choose a payment method.");
            redirectAttributes.addFlashAttribute("paymentMethod", normalizedPaymentMethod);
            return "redirect:/seeker/sessions/" + requestId + "/payment";
        }

        DemoSessionStore.SessionRequestView request = sessionStore.markPaymentPaid(requestId, normalizedPaymentMethod).orElse(null);
        if (request == null) {
            redirectAttributes.addFlashAttribute("formError", "Unable to process payment for this request.");
            return "redirect:/seeker/sessions/" + requestId;
        }

        redirectAttributes.addFlashAttribute("flashMessage", "Payment recorded successfully (demo mode).");
        return "redirect:/seeker/sessions/" + request.requestId();
    }

    @PostMapping("/seeker/sessions/{requestId}/payment/preview-complete")
    public String previewPaymentCompletion(
            @PathVariable String requestId,
            RedirectAttributes redirectAttributes
    ) {
        if (sessionStore.findRequest(requestId).isEmpty()) {
            redirectAttributes.addFlashAttribute("formError", "Session request not found.");
            return "redirect:/seeker/mentors";
        }

        redirectAttributes.addFlashAttribute("flashMessage", "Payment completion flow previewed (no changes made).");
        return "redirect:/seeker/sessions/" + requestId;
    }

    @PostMapping("/seeker/sessions/{requestId}/cancel")
    public String cancelAsMentee(
            @PathVariable String requestId,
            RedirectAttributes redirectAttributes
    ) {
        DemoSessionStore.SessionRequestView request = sessionStore.cancelRequest(requestId, DemoSessionStore.CancellationActor.MENTEE).orElse(null);
        if (request == null) {
            redirectAttributes.addFlashAttribute("formError", "Unable to cancel this session request.");
            return "redirect:/seeker/sessions/" + requestId;
        }

        redirectAttributes.addFlashAttribute("flashMessage", cancellationFlashMessage(request, "Mentee"));
        return "redirect:/seeker/sessions/" + request.requestId();
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

    @PostMapping("/mentor/sessions/{requestId}/cancel")
    public String cancelAsMentor(
            @PathVariable String requestId,
            RedirectAttributes redirectAttributes
    ) {
        DemoSessionStore.SessionRequestView request = sessionStore.cancelRequest(requestId, DemoSessionStore.CancellationActor.MENTOR).orElse(null);
        if (request == null) {
            redirectAttributes.addFlashAttribute("formError", "Unable to cancel this session.");
            return "redirect:/mentor/requests";
        }

        redirectAttributes.addFlashAttribute("flashMessage", cancellationFlashMessage(request, "Mentor"));
        return "redirect:/mentor/requests";
    }

    @PostMapping("/mentor/sessions/{requestId}/complete")
    public String completeSession(
            @PathVariable String requestId,
            RedirectAttributes redirectAttributes
    ) {
        DemoSessionStore.SessionRequestView request = sessionStore.markCompleted(requestId).orElse(null);
        if (request == null) {
            redirectAttributes.addFlashAttribute("formError", "Only paid sessions can be marked completed.");
            return "redirect:/mentor/requests";
        }

        redirectAttributes.addFlashAttribute("flashMessage", "Session completed. Mentor payout is now ready (demo mode).");
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

    private String quotePreviewLabel(DemoSessionStore.MentorDirectoryItemView mentor, String slotId) {
        if (mentor == null || isBlank(slotId)) {
            return "";
        }

        DemoSessionStore.AvailabilitySlotView slot = mentor.availability().stream()
                .filter(item -> item.slotId().equalsIgnoreCase(slotId))
                .findFirst()
                .orElse(null);
        if (slot == null) {
            return "";
        }

        int quotedAmount = quoteAmount(mentor, slot);
        return sessionStore.formatAmount(quotedAmount)
                + (mentor.defaultPricingModel() == DemoSessionStore.PricingModel.HOURLY ? " estimated for this slot" : " flat session rate");
    }

    private String pricingModelLabel(DemoSessionStore.MentorDirectoryItemView mentor) {
        if (mentor == null) {
            return "";
        }
        return mentor.defaultPricingModel() == DemoSessionStore.PricingModel.HOURLY
                ? "Hourly pricing"
                : "Flat session pricing";
    }

    private int quoteAmount(DemoSessionStore.MentorDirectoryItemView mentor, DemoSessionStore.AvailabilitySlotView slot) {
        if (mentor.defaultPricingModel() == DemoSessionStore.PricingModel.FLAT) {
            return mentor.flatRateCents() == null ? 0 : mentor.flatRateCents();
        }
        int hourly = mentor.hourlyRateCents() == null ? 0 : mentor.hourlyRateCents();
        long minutes = Math.max(1, java.time.Duration.between(slot.slotStartAt(), slot.slotEndAt()).toMinutes());
        return (int) ((hourly * minutes + 59) / 60);
    }

    private boolean canCancelAsMentee(DemoSessionStore.SessionStatus status) {
        return status == DemoSessionStore.SessionStatus.REQUESTED
                || status == DemoSessionStore.SessionStatus.APPROVED_PENDING_PAYMENT
                || status == DemoSessionStore.SessionStatus.APPROVED_PAID;
    }

    private String cancellationFlashMessage(DemoSessionStore.SessionRequestView request, String actorLabel) {
        String feeText = request.cancellationFeePercent() > 0
                ? request.cancellationFeePercent() + "% cancellation fee applied."
                : "No cancellation fee applied.";
        return actorLabel + " cancelled request " + request.requestId() + ". " + feeText;
    }

    private String cancellationLabel(DemoSessionStore.SessionRequestView request) {
        if (request.status() != DemoSessionStore.SessionStatus.CANCELLED) {
            return "";
        }
        return request.cancellationFeePercent() > 0
                ? "Cancelled within 24 hours. 50% cancellation fee applied."
                : "Cancelled more than 24 hours before session. No cancellation fee.";
    }

    private String toStatusLabel(DemoSessionStore.SessionStatus status) {
        return switch (status) {
            case REQUESTED -> "Requested";
            case APPROVED_PENDING_PAYMENT -> "Approved - payment pending";
            case APPROVED_PAID -> "Approved - paid";
            case DECLINED -> "Declined";
            case CANCELLED -> "Cancelled";
            case EXPIRED -> "Expired";
            case COMPLETED -> "Completed";
        };
    }

    private String toStatusClass(DemoSessionStore.SessionStatus status) {
        return switch (status) {
            case REQUESTED -> "statusBadge statusBadge--requested";
            case APPROVED_PENDING_PAYMENT -> "statusBadge statusBadge--pendingPayment";
            case APPROVED_PAID -> "statusBadge statusBadge--approved";
            case DECLINED -> "statusBadge statusBadge--declined";
            case CANCELLED -> "statusBadge statusBadge--cancelled";
            case EXPIRED -> "statusBadge statusBadge--expired";
            case COMPLETED -> "statusBadge statusBadge--completed";
        };
    }

    private String toPaymentStatusLabel(DemoSessionStore.PaymentStatus status) {
        return switch (status) {
            case NOT_STARTED -> "Not started";
            case PENDING -> "Pending";
            case PAID -> "Paid";
            case PARTIAL_REFUND -> "Partially refunded";
            case REFUNDED -> "Refunded";
            case FAILED -> "Failed";
        };
    }

    private String toPaymentStatusClass(DemoSessionStore.PaymentStatus status) {
        return switch (status) {
            case NOT_STARTED -> "statusBadge statusBadge--neutral";
            case PENDING -> "statusBadge statusBadge--pendingPayment";
            case PAID -> "statusBadge statusBadge--approved";
            case PARTIAL_REFUND -> "statusBadge statusBadge--cancelled";
            case REFUNDED -> "statusBadge statusBadge--expired";
            case FAILED -> "statusBadge statusBadge--declined";
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
