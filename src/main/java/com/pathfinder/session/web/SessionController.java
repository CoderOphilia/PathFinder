package com.pathfinder.session.web;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.service.UserService;
import com.pathfinder.auth.web.AuthController;
import com.pathfinder.mentor.domain.MentorProfile;
import com.pathfinder.mentor.service.MentorProfileService;
import com.pathfinder.session.domain.SessionRequest;
import com.pathfinder.session.domain.SessionStatus;
import com.pathfinder.session.service.SessionService;
import com.pathfinder.session.service.SessionService.BookingPolicy;
import com.stripe.Stripe;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Controller
public class SessionController {

    @Value("${stripe.api.secret.key:}")
    private String stripeApiKey;

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
    private final UserService userService;

    public SessionController(
            MentorProfileService mentorProfileService,
            SessionService sessionService,
            UserService userService
    ) {
        this.mentorProfileService = mentorProfileService;
        this.sessionService = sessionService;
        this.userService = userService;
    }

    @GetMapping({"/seeker/sessions/new", "/mentee/sessions/new"})
    public String newSessionRequest(
            @RequestParam(defaultValue = "") String mentor,
            HttpSession session,
            Model model
    ) {
        List<MentorDirectoryItemView> mentors = mentorProfileService.listPublicMentors().stream()
                .map(profile -> new MentorDirectoryItemView(
                        profile.name(),
                        profile.profileImageUrl(),
                        profile.rate(),
                        profile.offersFreeSession(),
                        profile.trialSessionLabel(),
                        profile.tagline()
                ))
                .sorted(Comparator.comparing(MentorDirectoryItemView::name))
                .toList();
        String selectedMentor = resolveMentorName(mentor, mentors);
        MentorDirectoryItemView selectedMentorInfo = mentors.stream()
                .filter(item -> item.name().equalsIgnoreCase(selectedMentor))
                .findFirst()
                .orElse(null);
        String mentorEmail = selectedMentorInfo == null ? "" : mentorProfileService.findMentorEmailByName(selectedMentorInfo.name());
        BookingPolicy bookingPolicy = resolveBookingPolicy(currentSessionEmail(session), mentorEmail);
        List<AvailabilitySlotView> availableSlots = buildAvailabilitySlots(selectedMentor, bookingPolicy);

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

        model.addAttribute("selectedQuoteLabel", buildSelectedQuoteLabel(selectedMentorInfo, bookingPolicy));
        model.addAttribute("selectedPricingLabel", buildSelectedPricingLabel(selectedMentorInfo, bookingPolicy));
        model.addAttribute("bookingPolicyMessage", buildBookingPolicyMessage(bookingPolicy));
        model.addAttribute("submitButtonLabel", bookingPolicy.freeSessionAvailable() ? "Request free session" : "Submit request");
        model.addAttribute("trialSessionLabel", selectedMentorInfo == null ? "" : selectedMentorInfo.trialSessionLabel());
        model.addAttribute("showTrialOnlySlots", bookingPolicy.freeSessionAvailable());
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

        try {
            String mentorEmail = mentorProfileService.findMentorEmailByName(mentorName);
            BookingPolicy bookingPolicy = resolveBookingPolicy(menteeEmail, mentorEmail);
            AvailabilitySlotView slot = buildAvailabilitySlots(mentorName, bookingPolicy).stream()
                    .filter(item -> item.slotId().equalsIgnoreCase(slotId))
                    .findFirst()
                    .orElse(null);
            if (slot == null) {
                return redirectToSessionFormWithError(
                        redirectAttributes, mentorName, slotId, sessionType, objective, bookingNotes,
                        "Select a valid mentor slot."
                );
            }
            SessionRequest request = sessionService.createSession(
                    menteeEmail,
                    mentorEmail,
                    mentorName,
                    slot.displayLabel(),
                    sessionType,
                    objective,
                    bookingNotes,
                    bookingPolicy.freeSessionAvailable()
            );
            redirectAttributes.addFlashAttribute(
                    "flashMessage",
                    request.isFreeSessionRequested()
                            ? "Free session request submitted successfully."
                            : "Paid session request submitted successfully."
            );
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
            @RequestParam(defaultValue = "false") boolean showDetails,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        SessionRequest request = sessionService.getSessionById(requestId);
        if (request == null) {
            redirectAttributes.addFlashAttribute("formError", "Session request not found.");
            return "redirect:/mentee/mentors";
        }
        if (request.getStatus() == SessionStatus.APPROVED
                && !request.isFreeSessionRequested()
                && !request.isPaymentCompleted()
                && !showDetails) {
            redirectAttributes.addFlashAttribute("flashMessage", "Your session was approved. Complete payment to confirm it.");
            return "redirect:/mentee/sessions/" + requestId + "/payment";
        }

        model.addAttribute("sessionRequest", request);
        User mentorUser = mentorProfileService.findMentorUserByEmail(request.getMentorEmail());
        model.addAttribute("mentorProfileImageUrl", mentorUser == null ? "" : mentorUser.getProfileImageUrl());
        model.addAttribute("submittedAtLabel", request.getCreatedAt().format(CREATED_AT_FORMATTER));
        model.addAttribute("statusLabel", toStatusLabel(request.getStatus()));
        model.addAttribute("statusClass", toStatusClass(request.getStatus()));
        model.addAttribute("paymentStatusLabel", toPaymentStatusLabel(request));
        model.addAttribute("paymentStatusClass", toPaymentStatusClass(request));
        model.addAttribute("quotedAmountLabel", request.isFreeSessionRequested()
                ? "Free"
                : formatCad(request.getQuotedAmountCents()));
        model.addAttribute("pricingModelLabel", request.isFreeSessionRequested()
                ? "One-time free intro session"
                : "Paid mentor session");
        model.addAttribute("paymentDueLabel", request.getStatus() == SessionStatus.APPROVED && !request.isFreeSessionRequested()
                ? "Complete Stripe checkout to confirm attendance."
                : "");
        model.addAttribute("canPay", request.getStatus() == SessionStatus.APPROVED && !request.isFreeSessionRequested());
        model.addAttribute("canCancelAsMentee", canCancelAsMentee(request.getStatus()));
        model.addAttribute("cancellationLabel", "");
        model.addAttribute("bookingPolicySummary", request.isFreeSessionRequested()
                ? "This booking used the mentor's free introductory session."
                : "This booking requires payment after mentor approval.");
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
        if (request.isFreeSessionRequested()) {
            redirectAttributes.addFlashAttribute("formError", "This is a free session and does not require payment.");
            return "redirect:/mentee/sessions/" + requestId;
        }

        if (!model.containsAttribute("paymentMethod")) {
            model.addAttribute("paymentMethod", "");
        }
        model.addAttribute("previewMode", preview);
        model.addAttribute("sessionRequest", request);
        model.addAttribute("quotedAmountLabel", formatCad(request.getQuotedAmountCents()));
        model.addAttribute("paymentDueLabel", request.getCreatedAt().format(CREATED_AT_FORMATTER));
        return renderMenteePage(model, "Session payment", "mentee/session_payment :: content");
    }

    @PostMapping({"/seeker/sessions/{requestId}/payment", "/mentee/sessions/{requestId}/payment"})
    public String submitPayment(
            @PathVariable Long requestId,
            RedirectAttributes redirectAttributes
    ) {
        SessionRequest request = sessionService.getSessionById(requestId);
        if (request == null) {
            redirectAttributes.addFlashAttribute("formError", "Session request not found.");
            return "redirect:/mentee/mentors";
        }

        if (request.isFreeSessionRequested()) {
            redirectAttributes.addFlashAttribute("formError", "This session is free and does not need checkout.");
            return "redirect:/mentee/sessions/" + requestId;
        }

        MentorProfile mentorProfile = mentorProfileService.findProfileByEmail(request.getMentorEmail());

        if (mentorProfile == null || mentorProfile.getHourlyRateCents() == null) {
            redirectAttributes.addFlashAttribute("formError", "Could not process payment: Mentor pricing is unavailable.");
            return "redirect:/mentee/sessions/" + requestId;
        }

        long sessionCostCents = mentorProfile.getHourlyRateCents();
        long taxCents = Math.round(sessionCostCents * 0.05);

        if (isBlank(stripeApiKey)) {
            redirectAttributes.addFlashAttribute("formError", "Stripe secret key is not configured.");
            return "redirect:/mentee/sessions/" + requestId;
        }

        Stripe.apiKey = stripeApiKey;

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl("http://localhost:8080/mentee/sessions/" + requestId + "/payment/success")
                    .setCancelUrl("http://localhost:8080/mentee/sessions/" + requestId + "/payment/cancel")
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                            .setCurrency("cad")
                                            .setUnitAmount(sessionCostCents)
                                            .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                    .setName("Session with " + request.getMentorName())
                                                    .build())
                                            .build())
                                    .build())
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                            .setCurrency("cad")
                                            .setUnitAmount(taxCents)
                                            .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                    .setName("Sales Tax (5%)")
                                                    .build())
                                            .build())
                                    .build())
                    .build();

            com.stripe.model.checkout.Session stripeSession = com.stripe.model.checkout.Session.create(params);
            return "redirect:" + stripeSession.getUrl();

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("formError", "Stripe Error: " + e.getMessage());
            return "redirect:/mentee/sessions/" + requestId;
        }
    }

    @GetMapping({"/seeker/sessions/{requestId}/payment/success", "/mentee/sessions/{requestId}/payment/success"})
    public String paymentSuccess(
            @PathVariable Long requestId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            sessionService.paySession(requestId);
            redirectAttributes.addFlashAttribute("flashMessage", "Payment successful! Your session is confirmed.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("formError", e.getMessage());
        }
        return "redirect:/mentee/sessions/" + requestId;
    }

    @GetMapping({"/seeker/sessions/{requestId}/payment/cancel", "/mentee/sessions/{requestId}/payment/cancel"})
    public String paymentCancel(
            @PathVariable Long requestId,
            RedirectAttributes redirectAttributes
    ) {
        redirectAttributes.addFlashAttribute("formError", "Checkout cancelled. You can pay whenever you are ready.");
        return "redirect:/mentee/sessions/" + requestId + "?showDetails=true";
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
        List<RequestQueueItemView> pendingRequests = requests.stream()
                .filter(request -> request.getStatus() == SessionStatus.REQUESTED)
                .map(this::toRequestQueueItem)
                .toList();
        List<RequestQueueItemView> previousRequests = requests.stream()
                .filter(request -> request.getStatus() != SessionStatus.REQUESTED)
                .map(this::toRequestQueueItem)
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
            @RequestParam(defaultValue = "") String meetingLink,
            RedirectAttributes redirectAttributes
    ) {
        try {
            if ("approve".equalsIgnoreCase(decision)) {
                sessionService.approveSession(requestId, meetingLink);
                redirectAttributes.addFlashAttribute("flashMessage", "Session approved.");
            } else if ("decline".equalsIgnoreCase(decision)) {
                sessionService.declineSession(requestId, meetingLink);
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

    private List<AvailabilitySlotView> buildAvailabilitySlots(String mentorName, BookingPolicy bookingPolicy) {
        String timezone = "America/Vancouver";
        if (bookingPolicy.freeSessionAvailable()) {
            MentorProfileService.TrialSessionAvailability trialAvailability =
                    mentorProfileService.findTrialAvailabilityByMentorName(mentorName);
            if (trialAvailability == null) {
                return List.of();
            }
            return List.of(new AvailabilitySlotView(
                    "trial-slot-" + trialAvailability.weekday() + "-" + trialAvailability.startTime().replace(":", ""),
                    weekdayLabel(trialAvailability.weekday()),
                    timeRange(trialAvailability.startTime(), trialAvailability.endTime()),
                    timezone
            ));
        }
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

    private String toPaymentStatusLabel(SessionRequest request) {
        if (request.isFreeSessionRequested()) {
            return "Not required";
        }
        if (request.isPaymentCompleted()) {
            return "Paid";
        }
        if (request.getStatus() == SessionStatus.APPROVED) {
            return "Ready to pay";
        }
        return "Not started";
    }

    private String toPaymentStatusClass(SessionRequest request) {
        if (request.isFreeSessionRequested()) {
            return "statusBadge statusBadge--neutral";
        }
        return request.isPaymentCompleted() ? "statusBadge statusBadge--approved" : "statusBadge statusBadge--neutral";
    }

    private String buildSelectedPricingLabel(MentorDirectoryItemView mentor, BookingPolicy bookingPolicy) {
        if (mentor == null) {
            return "";
        }
        if (bookingPolicy.freeSessionAvailable()) {
            return "Use one of your remaining platform trial sessions with this mentor";
        }
        if (bookingPolicy.mentorOffersFreeSession() && !bookingPolicy.mentorTrialSlotConfigured()) {
            return "Trial enabled, but the mentor has not published a trial slot yet";
        }
        if (bookingPolicy.mentorOffersFreeSession() && bookingPolicy.remainingPlatformTrialSessions() == 0) {
            return "Your 2 free platform trial sessions are used up; paid mentor pricing applies";
        }
        if (bookingPolicy.mentorOffersFreeSession() && bookingPolicy.menteeAlreadyUsedFreeSessionWithMentor()) {
            return "You already used this mentor's trial session; standard mentor pricing applies";
        }
        return "Mentor profile pricing";
    }

    private String buildSelectedQuoteLabel(MentorDirectoryItemView mentor, BookingPolicy bookingPolicy) {
        if (mentor == null) {
            return "";
        }
        return bookingPolicy.freeSessionAvailable() ? "Free introductory session" : mentor.rate();
    }

    private String buildBookingPolicyMessage(BookingPolicy bookingPolicy) {
        if (bookingPolicy.freeSessionAvailable()) {
            return "You have " + bookingPolicy.remainingPlatformTrialSessions()
                    + " platform trial session(s) left, so this mentor's trial slot can be requested for free.";
        }
        if (bookingPolicy.mentorOffersFreeSession() && !bookingPolicy.mentorTrialSlotConfigured()) {
            return "This mentor offers trial sessions, but they still need to set a trial slot on their mentor profile.";
        }
        if (bookingPolicy.mentorOffersFreeSession() && bookingPolicy.remainingPlatformTrialSessions() == 0) {
            return "You have already used your 2 free platform trial sessions. New bookings now require payment after approval.";
        }
        if (bookingPolicy.mentorOffersFreeSession() && bookingPolicy.menteeAlreadyUsedFreeSessionWithMentor()) {
            return "You have already used this mentor's free trial session. New bookings with them require payment after approval.";
        }
        return "This mentor does not offer a free introductory session. Payment is required after approval.";
    }

    private BookingPolicy resolveBookingPolicy(String menteeEmail, String mentorEmail) {
        if (isBlank(mentorEmail)) {
            return new BookingPolicy(false, false, false, false, 0, 2);
        }
        return sessionService.getBookingPolicy(menteeEmail, mentorEmail);
    }

    private String formatCad(Integer amountCents) {
        if (amountCents == null) {
            return "$0.00";
        }
        return String.format(Locale.ROOT, "$%.2f", amountCents / 100.0);
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

    private RequestQueueItemView toRequestQueueItem(SessionRequest request) {
        User menteeUser = userService.findUserByEmail(request.getMenteeEmail());
        String menteeName = menteeUser == null
                ? request.getMenteeEmail()
                : buildFullName(menteeUser.getFirstName(), menteeUser.getLastName(), request.getMenteeEmail());
        String profileImageUrl = menteeUser == null ? "" : menteeUser.getProfileImageUrl();
        return new RequestQueueItemView(request, menteeName, profileImageUrl);
    }

    private String buildFullName(String firstName, String lastName, String fallbackEmail) {
        String combined = ((firstName == null ? "" : firstName.trim()) + " " + (lastName == null ? "" : lastName.trim())).trim();
        return combined.isEmpty() ? fallbackEmail : combined;
    }

    private record MentorDirectoryItemView(
            String name,
            String profileImageUrl,
            String rate,
            boolean offersFreeSession,
            String trialSessionLabel,
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

    private record RequestQueueItemView(
            SessionRequest request,
            String menteeName,
            String menteeProfileImageUrl
    ) {
    }
}
