package com.pathfinder.session.web;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class DemoSessionStore {

    private static final int REQUEST_EXPIRY_HOURS = 24;
    private static final int SESSION_DURATION_MINUTES = 45;
    private static final int CANCELLATION_FEE_PERCENT = 50;
    private static final DateTimeFormatter WEEKDAY_FORMATTER = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private static final Comparator<SessionRequestView> REQUEST_ORDER = Comparator
            .comparing((SessionRequestView request) -> queuePriority(request.status()))
            .thenComparing(SessionRequestView::submittedAt, Comparator.reverseOrder());

    private final Clock clock;
    private final List<MentorDirectoryItemView> mentors;
    private final AtomicInteger requestSequence = new AtomicInteger(1000);
    private final Map<String, SessionRequestView> requestsById = new LinkedHashMap<>();
    private final Map<String, SlotLockView> slotLocksByKey = new HashMap<>();
    private Duration demoTimeOffset = Duration.ZERO;

    public DemoSessionStore() {
        this(Clock.systemDefaultZone());
    }

    DemoSessionStore(Clock clock) {
        this.clock = clock;
        this.mentors = buildMentorDirectory();
    }

    public synchronized List<MentorDirectoryItemView> getMentors() {
        expirePendingRequests();
        return mentors;
    }

    public synchronized List<AvailabilitySlotView> getAvailabilityForMentor(String mentorName) {
        expirePendingRequests();
        return getMentorByNameInternal(mentorName)
                .map(MentorDirectoryItemView::availability)
                .orElse(List.of())
                .stream()
                .filter(slot -> !isSlotLocked(slot.mentorName(), slot.slotId()))
                .toList();
    }

    public synchronized Optional<MentorDirectoryItemView> getMentorByName(String mentorName) {
        expirePendingRequests();
        return getMentorByNameInternal(mentorName);
    }

    private Optional<MentorDirectoryItemView> getMentorByNameInternal(String mentorName) {
        if (isBlank(mentorName)) {
            return Optional.empty();
        }
        return mentors.stream()
                .filter(mentor -> mentor.name().equalsIgnoreCase(mentorName.trim()))
                .findFirst();
    }

    public synchronized boolean isSlotValidForMentor(String mentorName, String slotId) {
        expirePendingRequests();
        if (isBlank(mentorName) || isBlank(slotId)) {
            return false;
        }
        return getAvailabilitySlots(mentorName).stream()
                .anyMatch(slot -> slot.slotId().equalsIgnoreCase(slotId.trim()));
    }

    public synchronized boolean isSlotAvailableForMentor(String mentorName, String slotId) {
        expirePendingRequests();
        if (isBlank(mentorName) || isBlank(slotId)) {
            return false;
        }
        return getAvailabilitySlots(mentorName).stream()
                .anyMatch(slot -> slot.slotId().equalsIgnoreCase(slotId.trim()) && !isSlotLocked(mentorName, slotId));
    }

    public synchronized Optional<AvailabilitySlotView> getSlot(String mentorName, String slotId) {
        expirePendingRequests();
        return getSlotInternal(mentorName, slotId);
    }

    private Optional<AvailabilitySlotView> getSlotInternal(String mentorName, String slotId) {
        if (isBlank(mentorName) || isBlank(slotId)) {
            return Optional.empty();
        }
        return getAvailabilitySlots(mentorName).stream()
                .filter(slot -> slot.slotId().equalsIgnoreCase(slotId.trim()))
                .findFirst();
    }

    public synchronized SessionRequestView createRequest(
            String mentorName,
            String slotId,
            String sessionType,
            String objective,
            String bookingNotes
    ) {
        expirePendingRequests();
        String normalizedMentorName = safeTrim(mentorName);
        String normalizedSlotId = safeTrim(slotId);

        MentorDirectoryItemView mentor = getMentorByNameInternal(normalizedMentorName)
                .orElseThrow(() -> new IllegalArgumentException("Invalid mentor."));

        if (!isSlotAvailableForMentor(normalizedMentorName, normalizedSlotId)) {
            throw new IllegalStateException("Selected slot is already reserved.");
        }

        AvailabilitySlotView slot = getSlotInternal(normalizedMentorName, normalizedSlotId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid slot for mentor."));

        String requestId = String.format(Locale.ROOT, "REQ-%04d", requestSequence.incrementAndGet());
        LocalDateTime submittedAt = now();
        LocalDateTime expiresAt = submittedAt.plusHours(REQUEST_EXPIRY_HOURS);
        int quotedAmountCents = quoteAmountCents(mentor.defaultPricingModel(), mentor.hourlyRateCents(), mentor.flatRateCents(), slot);

        SessionRequestView request = new SessionRequestView(
                requestId,
                slot.mentorName(),
                slot.slotId(),
                slot.displayLabel(),
                sessionType.trim(),
                objective.trim(),
                safeTrim(bookingNotes),
                "",
                SessionStatus.REQUESTED,
                PaymentStatus.NOT_STARTED,
                mentor.defaultPricingModel(),
                mentor.hourlyRateCents(),
                mentor.flatRateCents(),
                quotedAmountCents,
                slot.slotStartAt().minusHours(1),
                submittedAt,
                expiresAt,
                slot.slotStartAt(),
                slot.slotEndAt(),
                null,
                "",
                0
        );
        requestsById.put(requestId, request);
        slotLocksByKey.put(
                lockKey(slot.mentorName(), slot.slotId()),
                new SlotLockView(
                        slot.mentorName(),
                        slot.slotId(),
                        SlotLockState.HELD,
                        expiresAt,
                        requestId,
                        slot.slotStartAt(),
                        slot.slotEndAt()
                )
        );
        return request;
    }

    public synchronized Optional<SessionRequestView> findRequest(String requestId) {
        expirePendingRequests();
        return Optional.ofNullable(requestsById.get(requestId));
    }

    public synchronized List<SessionRequestView> listRequestsForMentorQueue() {
        expirePendingRequests();
        return requestsById.values().stream()
                .sorted(REQUEST_ORDER)
                .toList();
    }

    public synchronized Optional<SessionRequestView> applyDecision(String requestId, String decision, String mentorNote) {
        expirePendingRequests();
        SessionRequestView current = requestsById.get(requestId);
        if (current == null || current.status() != SessionStatus.REQUESTED) {
            return Optional.empty();
        }

        String normalized = safeTrim(decision).toLowerCase(Locale.ROOT);
        SessionRequestView updated;
        if ("approve".equals(normalized)) {
            updated = new SessionRequestView(
                    current.requestId(),
                    current.mentorName(),
                    current.slotId(),
                    current.slotLabel(),
                    current.sessionType(),
                    current.objective(),
                    current.bookingNotes(),
                    safeTrim(mentorNote),
                    SessionStatus.APPROVED_PENDING_PAYMENT,
                    PaymentStatus.NOT_STARTED,
                    current.pricingModelSnapshot(),
                    current.hourlyRateCentsSnapshot(),
                    current.flatRateCentsSnapshot(),
                    current.quotedAmountCents(),
                    current.paymentDueAt(),
                    current.submittedAt(),
                    null,
                    current.slotStartAt(),
                    current.slotEndAt(),
                    null,
                    "",
                    0
            );
            updateSlotLockState(updated, SlotLockState.CONFIRMED, null);
        } else if ("decline".equals(normalized)) {
            updated = new SessionRequestView(
                    current.requestId(),
                    current.mentorName(),
                    current.slotId(),
                    current.slotLabel(),
                    current.sessionType(),
                    current.objective(),
                    current.bookingNotes(),
                    safeTrim(mentorNote),
                    SessionStatus.DECLINED,
                    current.paymentStatus(),
                    current.pricingModelSnapshot(),
                    current.hourlyRateCentsSnapshot(),
                    current.flatRateCentsSnapshot(),
                    current.quotedAmountCents(),
                    current.paymentDueAt(),
                    current.submittedAt(),
                    null,
                    current.slotStartAt(),
                    current.slotEndAt(),
                    null,
                    "",
                    0
            );
            releaseSlotLock(updated.mentorName(), updated.slotId());
        } else {
            return Optional.empty();
        }

        requestsById.put(current.requestId(), updated);
        return Optional.of(updated);
    }

    public synchronized Optional<SessionRequestView> markPaymentPaid(String requestId, String paymentMethod) {
        expirePendingRequests();
        SessionRequestView current = requestsById.get(requestId);
        if (current == null || current.status() != SessionStatus.APPROVED_PENDING_PAYMENT || isBlank(paymentMethod)) {
            return Optional.empty();
        }

        SessionRequestView updated = new SessionRequestView(
                current.requestId(),
                current.mentorName(),
                current.slotId(),
                current.slotLabel(),
                current.sessionType(),
                current.objective(),
                current.bookingNotes(),
                current.mentorNote(),
                SessionStatus.APPROVED_PAID,
                PaymentStatus.PAID,
                current.pricingModelSnapshot(),
                current.hourlyRateCentsSnapshot(),
                current.flatRateCentsSnapshot(),
                current.quotedAmountCents(),
                current.paymentDueAt(),
                current.submittedAt(),
                null,
                current.slotStartAt(),
                current.slotEndAt(),
                null,
                "",
                0
        );
        requestsById.put(current.requestId(), updated);
        return Optional.of(updated);
    }

    public synchronized Optional<SessionRequestView> cancelRequest(String requestId, CancellationActor actor) {
        expirePendingRequests();
        SessionRequestView current = requestsById.get(requestId);
        if (current == null || isTerminal(current.status())) {
            return Optional.empty();
        }

        LocalDateTime now = now();
        boolean within24Hours = !now.isBefore(current.slotStartAt().minusHours(24));
        int cancellationFeePercent = within24Hours ? CANCELLATION_FEE_PERCENT : 0;
        PaymentStatus cancellationPaymentStatus = resolveCancellationPaymentStatus(current.paymentStatus(), cancellationFeePercent);

        SessionRequestView updated = new SessionRequestView(
                current.requestId(),
                current.mentorName(),
                current.slotId(),
                current.slotLabel(),
                current.sessionType(),
                current.objective(),
                current.bookingNotes(),
                current.mentorNote(),
                SessionStatus.CANCELLED,
                cancellationPaymentStatus,
                current.pricingModelSnapshot(),
                current.hourlyRateCentsSnapshot(),
                current.flatRateCentsSnapshot(),
                current.quotedAmountCents(),
                current.paymentDueAt(),
                current.submittedAt(),
                null,
                current.slotStartAt(),
                current.slotEndAt(),
                now,
                actor.name().toLowerCase(Locale.ROOT),
                cancellationFeePercent
        );
        requestsById.put(current.requestId(), updated);
        releaseSlotLock(updated.mentorName(), updated.slotId());
        return Optional.of(updated);
    }

    public synchronized Optional<SessionRequestView> markCompleted(String requestId) {
        expirePendingRequests();
        SessionRequestView current = requestsById.get(requestId);
        if (current == null || current.status() != SessionStatus.APPROVED_PAID) {
            return Optional.empty();
        }

        SessionRequestView updated = new SessionRequestView(
                current.requestId(),
                current.mentorName(),
                current.slotId(),
                current.slotLabel(),
                current.sessionType(),
                current.objective(),
                current.bookingNotes(),
                current.mentorNote(),
                SessionStatus.COMPLETED,
                current.paymentStatus(),
                current.pricingModelSnapshot(),
                current.hourlyRateCentsSnapshot(),
                current.flatRateCentsSnapshot(),
                current.quotedAmountCents(),
                current.paymentDueAt(),
                current.submittedAt(),
                null,
                current.slotStartAt(),
                current.slotEndAt(),
                current.cancelledAt(),
                current.cancelledBy(),
                current.cancellationFeePercent()
        );
        requestsById.put(current.requestId(), updated);
        return Optional.of(updated);
    }

    public synchronized int expirePendingRequests() {
        LocalDateTime now = now();
        int expiredCount = 0;
        for (SessionRequestView request : List.copyOf(requestsById.values())) {
            if (request.status() == SessionStatus.REQUESTED
                    && request.expiresAt() != null
                    && now.isAfter(request.expiresAt())) {
                SessionRequestView expired = new SessionRequestView(
                        request.requestId(),
                        request.mentorName(),
                        request.slotId(),
                        request.slotLabel(),
                        request.sessionType(),
                        request.objective(),
                        request.bookingNotes(),
                        request.mentorNote(),
                        SessionStatus.EXPIRED,
                        request.paymentStatus(),
                        request.pricingModelSnapshot(),
                        request.hourlyRateCentsSnapshot(),
                        request.flatRateCentsSnapshot(),
                        request.quotedAmountCents(),
                        request.paymentDueAt(),
                        request.submittedAt(),
                        null,
                        request.slotStartAt(),
                        request.slotEndAt(),
                        request.cancelledAt(),
                        request.cancelledBy(),
                        request.cancellationFeePercent()
                );
                requestsById.put(request.requestId(), expired);
                releaseSlotLock(request.mentorName(), request.slotId());
                expiredCount++;
            } else if (request.status() == SessionStatus.APPROVED_PENDING_PAYMENT
                    && request.paymentDueAt() != null
                    && now.isAfter(request.paymentDueAt())) {
                SessionRequestView cancelled = new SessionRequestView(
                        request.requestId(),
                        request.mentorName(),
                        request.slotId(),
                        request.slotLabel(),
                        request.sessionType(),
                        request.objective(),
                        request.bookingNotes(),
                        request.mentorNote(),
                        SessionStatus.CANCELLED,
                        PaymentStatus.FAILED,
                        request.pricingModelSnapshot(),
                        request.hourlyRateCentsSnapshot(),
                        request.flatRateCentsSnapshot(),
                        request.quotedAmountCents(),
                        request.paymentDueAt(),
                        request.submittedAt(),
                        null,
                        request.slotStartAt(),
                        request.slotEndAt(),
                        now,
                        "system",
                        0
                );
                requestsById.put(request.requestId(), cancelled);
                releaseSlotLock(request.mentorName(), request.slotId());
            }
        }

        for (SlotLockView lock : List.copyOf(slotLocksByKey.values())) {
            if (lock.lockState() == SlotLockState.HELD && lock.expiresAt() != null && now.isAfter(lock.expiresAt())) {
                slotLocksByKey.remove(lockKey(lock.mentorName(), lock.slotId()));
            }
        }
        return expiredCount;
    }

    public synchronized void advanceTime(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        demoTimeOffset = demoTimeOffset.plus(duration);
        expirePendingRequests();
    }

    public synchronized String formatAmount(int amountCents) {
        return String.format(Locale.ROOT, "$%.2f", amountCents / 100.0);
    }

    public synchronized void reset() {
        requestsById.clear();
        slotLocksByKey.clear();
        requestSequence.set(1000);
        demoTimeOffset = Duration.ZERO;
    }

    private boolean isTerminal(SessionStatus status) {
        return status == SessionStatus.DECLINED
                || status == SessionStatus.CANCELLED
                || status == SessionStatus.EXPIRED
                || status == SessionStatus.COMPLETED;
    }

    private PaymentStatus resolveCancellationPaymentStatus(PaymentStatus currentStatus, int cancellationFeePercent) {
        if (currentStatus == PaymentStatus.PAID) {
            return cancellationFeePercent > 0 ? PaymentStatus.PARTIAL_REFUND : PaymentStatus.REFUNDED;
        }
        if (currentStatus == PaymentStatus.PENDING) {
            return PaymentStatus.FAILED;
        }
        return currentStatus;
    }

    private int quoteAmountCents(PricingModel pricingModel, Integer hourlyRateCents, Integer flatRateCents, AvailabilitySlotView slot) {
        if (pricingModel == PricingModel.FLAT) {
            return nonNegative(flatRateCents);
        }

        int hourly = nonNegative(hourlyRateCents);
        long durationMinutes = Math.max(1, Duration.between(slot.slotStartAt(), slot.slotEndAt()).toMinutes());
        return (int) ((hourly * durationMinutes + 59) / 60);
    }

    private int nonNegative(Integer amount) {
        if (amount == null) {
            return 0;
        }
        return Math.max(amount, 0);
    }

    private void updateSlotLockState(SessionRequestView request, SlotLockState lockState, LocalDateTime expiresAt) {
        slotLocksByKey.put(
                lockKey(request.mentorName(), request.slotId()),
                new SlotLockView(
                        request.mentorName(),
                        request.slotId(),
                        lockState,
                        expiresAt,
                        request.requestId(),
                        request.slotStartAt(),
                        request.slotEndAt()
                )
        );
    }

    private void releaseSlotLock(String mentorName, String slotId) {
        slotLocksByKey.remove(lockKey(mentorName, slotId));
    }

    private boolean isSlotLocked(String mentorName, String slotId) {
        SlotLockView lock = slotLocksByKey.get(lockKey(mentorName, slotId));
        if (lock == null) {
            return false;
        }
        if (lock.lockState() == SlotLockState.HELD && lock.expiresAt() != null && now().isAfter(lock.expiresAt())) {
            slotLocksByKey.remove(lockKey(mentorName, slotId));
            return false;
        }
        return true;
    }

    private String lockKey(String mentorName, String slotId) {
        return safeTrim(mentorName).toLowerCase(Locale.ROOT) + "|" + safeTrim(slotId).toLowerCase(Locale.ROOT);
    }

    private List<AvailabilitySlotView> getAvailabilitySlots(String mentorName) {
        return getMentorByNameInternal(mentorName)
                .map(MentorDirectoryItemView::availability)
                .orElse(List.of());
    }

    private List<MentorDirectoryItemView> buildMentorDirectory() {
        LocalDateTime base = LocalDateTime.now(clock)
                .plusHours(2)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        return List.of(
                createMentor(
                        "PRI",
                        "Priya K.",
                        PricingModel.HOURLY,
                        8000,
                        null,
                        "Backend engineering mentor for interview prep and career growth.",
                        "America/Vancouver",
                        base,
                        8, 30, 52
                ),
                createMentor(
                        "ALE",
                        "Alex M.",
                        PricingModel.FLAT,
                        null,
                        12000,
                        "System design and architecture mentor for mid-level engineers.",
                        "America/Vancouver",
                        base,
                        10, 34, 58
                ),
                createMentor(
                        "NAT",
                        "Natalie R.",
                        PricingModel.HOURLY,
                        7000,
                        null,
                        "Career-switch guidance, resume storytelling, and interview confidence.",
                        "America/Vancouver",
                        base,
                        12, 36, 60
                ),
                createMentor(
                        "MAR",
                        "Marcus L.",
                        PricingModel.FLAT,
                        null,
                        10000,
                        "Product strategy mentor for PM interview loops and case practice.",
                        "America/Vancouver",
                        base,
                        14, 40, 66
                ),
                createMentor(
                        "SON",
                        "Sonia V.",
                        PricingModel.HOURLY,
                        9000,
                        null,
                        "Data and analytics coaching for SQL, metrics, and experimentation.",
                        "America/Vancouver",
                        base,
                        16, 42, 70
                )
        );
    }

    private MentorDirectoryItemView createMentor(
            String mentorCode,
            String mentorName,
            PricingModel pricingModel,
            Integer hourlyRateCents,
            Integer flatRateCents,
            String tagline,
            String timezone,
            LocalDateTime base,
            int... slotOffsetsHours
    ) {
        List<AvailabilitySlotView> slots = new ArrayList<>();
        for (int index = 0; index < slotOffsetsHours.length; index++) {
            slots.add(createSlot(mentorCode, mentorName, timezone, base.plusHours(slotOffsetsHours[index]), index + 1));
        }

        return new MentorDirectoryItemView(
                mentorName,
                pricingLabel(pricingModel, hourlyRateCents, flatRateCents),
                tagline,
                timezone,
                pricingModel,
                hourlyRateCents,
                flatRateCents,
                slots
        );
    }

    private AvailabilitySlotView createSlot(
            String mentorCode,
            String mentorName,
            String timezone,
            LocalDateTime slotStart,
            int index
    ) {
        LocalDateTime slotEnd = slotStart.plusMinutes(SESSION_DURATION_MINUTES);
        String slotId = mentorCode + "-SLOT-" + index;
        String weekday = slotStart.format(WEEKDAY_FORMATTER);
        String timeRange = slotStart.format(TIME_FORMATTER) + " - " + slotEnd.format(TIME_FORMATTER);
        return new AvailabilitySlotView(slotId, mentorName, weekday, timeRange, timezone, slotStart, slotEnd);
    }

    private String pricingLabel(PricingModel pricingModel, Integer hourlyRateCents, Integer flatRateCents) {
        int hourly = nonNegative(hourlyRateCents);
        int flat = nonNegative(flatRateCents);
        if (pricingModel == PricingModel.FLAT) {
            return formatAmount(flat) + " flat";
        }
        return formatAmount(hourly) + "/hr";
    }

    private static int queuePriority(SessionStatus status) {
        return switch (status) {
            case REQUESTED -> 0;
            case APPROVED_PENDING_PAYMENT -> 1;
            case APPROVED_PAID -> 2;
            case COMPLETED -> 3;
            case CANCELLED -> 4;
            case EXPIRED -> 5;
            case DECLINED -> 6;
        };
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).plus(demoTimeOffset);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public enum SessionStatus {
        REQUESTED,
        APPROVED_PENDING_PAYMENT,
        APPROVED_PAID,
        DECLINED,
        CANCELLED,
        EXPIRED,
        COMPLETED
    }

    public enum PaymentStatus {
        NOT_STARTED,
        PENDING,
        PAID,
        PARTIAL_REFUND,
        REFUNDED,
        FAILED
    }

    public enum PricingModel {
        HOURLY,
        FLAT
    }

    public enum CancellationActor {
        MENTEE,
        MENTOR,
        ADMIN
    }

    public enum SlotLockState {
        HELD,
        CONFIRMED
    }

    public record AvailabilitySlotView(
            String slotId,
            String mentorName,
            String weekday,
            String timeRange,
            String timezone,
            LocalDateTime slotStartAt,
            LocalDateTime slotEndAt
    ) {
        public String displayLabel() {
            return weekday + " • " + timeRange + " (" + timezone + ")";
        }
    }

    public record SessionRequestView(
            String requestId,
            String mentorName,
            String slotId,
            String slotLabel,
            String sessionType,
            String objective,
            String bookingNotes,
            String mentorNote,
            SessionStatus status,
            PaymentStatus paymentStatus,
            PricingModel pricingModelSnapshot,
            Integer hourlyRateCentsSnapshot,
            Integer flatRateCentsSnapshot,
            int quotedAmountCents,
            LocalDateTime paymentDueAt,
            LocalDateTime submittedAt,
            LocalDateTime expiresAt,
            LocalDateTime slotStartAt,
            LocalDateTime slotEndAt,
            LocalDateTime cancelledAt,
            String cancelledBy,
            int cancellationFeePercent
    ) {
    }

    public record MentorDirectoryItemView(
            String name,
            String rate,
            String tagline,
            String timezone,
            PricingModel defaultPricingModel,
            Integer hourlyRateCents,
            Integer flatRateCents,
            List<AvailabilitySlotView> availability
    ) {
        public List<AvailabilitySlotView> availability() {
            return new ArrayList<>(availability);
        }
    }

    public record SlotLockView(
            String mentorName,
            String slotId,
            SlotLockState lockState,
            LocalDateTime expiresAt,
            String sessionRequestId,
            LocalDateTime slotStartAt,
            LocalDateTime slotEndAt
    ) {
    }
}
