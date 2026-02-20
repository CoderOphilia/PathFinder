package com.pathfinder.session.web;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class DemoSessionStore {

    private static final List<MentorDirectoryItemView> MENTORS = List.of(
            new MentorDirectoryItemView(
                    "Priya K.",
                    "$80/hr",
                    "Backend engineering mentor for interview prep and career growth.",
                    List.of(
                            new AvailabilitySlotView("PRI-MON-1800", "Priya K.", "Monday", "6:00 PM - 6:45 PM", "America/Vancouver"),
                            new AvailabilitySlotView("PRI-WED-1700", "Priya K.", "Wednesday", "5:00 PM - 5:45 PM", "America/Vancouver"),
                            new AvailabilitySlotView("PRI-SAT-1100", "Priya K.", "Saturday", "11:00 AM - 11:45 AM", "America/Vancouver")
                    )
            ),
            new MentorDirectoryItemView(
                    "Alex M.",
                    "$95/hr",
                    "System design and architecture mentor for mid-level engineers.",
                    List.of(
                            new AvailabilitySlotView("ALE-TUE-1800", "Alex M.", "Tuesday", "6:00 PM - 6:45 PM", "America/Vancouver"),
                            new AvailabilitySlotView("ALE-THU-1900", "Alex M.", "Thursday", "7:00 PM - 7:45 PM", "America/Vancouver"),
                            new AvailabilitySlotView("ALE-SUN-1000", "Alex M.", "Sunday", "10:00 AM - 10:45 AM", "America/Vancouver")
                    )
            ),
            new MentorDirectoryItemView(
                    "Natalie R.",
                    "$70/hr",
                    "Career-switch guidance, resume storytelling, and interview confidence.",
                    List.of(
                            new AvailabilitySlotView("NAT-MON-1600", "Natalie R.", "Monday", "4:00 PM - 4:45 PM", "America/Vancouver"),
                            new AvailabilitySlotView("NAT-THU-1700", "Natalie R.", "Thursday", "5:00 PM - 5:45 PM", "America/Vancouver"),
                            new AvailabilitySlotView("NAT-SAT-1330", "Natalie R.", "Saturday", "1:30 PM - 2:15 PM", "America/Vancouver")
                    )
            ),
            new MentorDirectoryItemView(
                    "Marcus L.",
                    "$85/hr",
                    "Product strategy mentor for PM interview loops and case practice.",
                    List.of(
                            new AvailabilitySlotView("MAR-TUE-1700", "Marcus L.", "Tuesday", "5:00 PM - 5:45 PM", "America/Vancouver"),
                            new AvailabilitySlotView("MAR-FRI-1200", "Marcus L.", "Friday", "12:00 PM - 12:45 PM", "America/Vancouver"),
                            new AvailabilitySlotView("MAR-SUN-1400", "Marcus L.", "Sunday", "2:00 PM - 2:45 PM", "America/Vancouver")
                    )
            ),
            new MentorDirectoryItemView(
                    "Sonia V.",
                    "$90/hr",
                    "Data and analytics coaching for SQL, metrics, and experimentation.",
                    List.of(
                            new AvailabilitySlotView("SON-WED-1830", "Sonia V.", "Wednesday", "6:30 PM - 7:15 PM", "America/Vancouver"),
                            new AvailabilitySlotView("SON-THU-1600", "Sonia V.", "Thursday", "4:00 PM - 4:45 PM", "America/Vancouver"),
                            new AvailabilitySlotView("SON-SAT-0900", "Sonia V.", "Saturday", "9:00 AM - 9:45 AM", "America/Vancouver")
                    )
            )
    );

    private static final Comparator<SessionRequestView> REQUEST_ORDER = Comparator
            .comparing((SessionRequestView request) -> request.status() == SessionStatus.REQUESTED ? 0 : 1)
            .thenComparing(SessionRequestView::submittedAt, Comparator.reverseOrder());

    private final AtomicInteger requestSequence = new AtomicInteger(1000);
    private final Map<String, SessionRequestView> requestsById = new LinkedHashMap<>();

    public List<MentorDirectoryItemView> getMentors() {
        return MENTORS;
    }

    public List<AvailabilitySlotView> getAvailabilityForMentor(String mentorName) {
        return getMentorByName(mentorName)
                .map(MentorDirectoryItemView::availability)
                .orElse(List.of());
    }

    public Optional<MentorDirectoryItemView> getMentorByName(String mentorName) {
        if (isBlank(mentorName)) {
            return Optional.empty();
        }
        return MENTORS.stream()
                .filter(mentor -> mentor.name().equalsIgnoreCase(mentorName.trim()))
                .findFirst();
    }

    public boolean isSlotValidForMentor(String mentorName, String slotId) {
        if (isBlank(mentorName) || isBlank(slotId)) {
            return false;
        }
        return getAvailabilityForMentor(mentorName).stream()
                .anyMatch(slot -> slot.slotId().equalsIgnoreCase(slotId.trim()));
    }

    public Optional<AvailabilitySlotView> getSlot(String mentorName, String slotId) {
        if (isBlank(mentorName) || isBlank(slotId)) {
            return Optional.empty();
        }
        return getAvailabilityForMentor(mentorName).stream()
                .filter(slot -> slot.slotId().equalsIgnoreCase(slotId.trim()))
                .findFirst();
    }

    public SessionRequestView createRequest(
            String mentorName,
            String slotId,
            String sessionType,
            String objective,
            String bookingNotes
    ) {
        AvailabilitySlotView slot = getSlot(mentorName, slotId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid slot for mentor."));

        String requestId = String.format(Locale.ROOT, "REQ-%04d", requestSequence.incrementAndGet());
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
                LocalDateTime.now()
        );
        requestsById.put(requestId, request);
        return request;
    }

    public Optional<SessionRequestView> findRequest(String requestId) {
        return Optional.ofNullable(requestsById.get(requestId));
    }

    public List<SessionRequestView> listRequestsForMentorQueue() {
        return requestsById.values().stream()
                .sorted(REQUEST_ORDER)
                .toList();
    }

    public Optional<SessionRequestView> applyDecision(String requestId, String decision, String mentorNote) {
        SessionRequestView current = requestsById.get(requestId);
        if (current == null) {
            return Optional.empty();
        }

        String normalized = safeTrim(decision).toLowerCase(Locale.ROOT);
        SessionStatus updatedStatus = switch (normalized) {
            case "approve" -> SessionStatus.APPROVED;
            case "decline" -> SessionStatus.DECLINED;
            default -> null;
        };
        if (updatedStatus == null) {
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
                safeTrim(mentorNote),
                updatedStatus,
                current.submittedAt()
        );
        requestsById.put(current.requestId(), updated);
        return Optional.of(updated);
    }

    public void reset() {
        requestsById.clear();
        requestSequence.set(1000);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public enum SessionStatus {
        REQUESTED,
        APPROVED,
        DECLINED
    }

    public record AvailabilitySlotView(
            String slotId,
            String mentorName,
            String weekday,
            String timeRange,
            String timezone
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
            LocalDateTime submittedAt
    ) {
    }

    public record MentorDirectoryItemView(
            String name,
            String rate,
            String tagline,
            List<AvailabilitySlotView> availability
    ) {
        public List<AvailabilitySlotView> availability() {
            return new ArrayList<>(availability);
        }
    }
}
