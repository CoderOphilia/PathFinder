package com.pathfinder.admin.service;

import com.pathfinder.session.domain.SessionRequest;
import com.pathfinder.session.domain.SessionStatus;
import com.pathfinder.session.repo.SessionRequestRepository;
import com.pathfinder.session.service.SessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class AdminSessionOversightService {

    private static final DateTimeFormatter SUBMITTED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a", Locale.ENGLISH);

    private final SessionRequestRepository sessionRequestRepository;
    private final SessionService sessionService;

    public AdminSessionOversightService(
            SessionRequestRepository sessionRequestRepository,
            SessionService sessionService
    ) {
        this.sessionRequestRepository = sessionRequestRepository;
        this.sessionService = sessionService;
    }

    @Transactional(readOnly = true)
    public List<SessionOversightItemView> listRequests() {
        return sessionRequestRepository.findAll().stream()
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .map(this::toView)
                .toList();
    }

    public void cancelRequest(Long requestId) {
        if (requestId == null) {
            throw new IllegalArgumentException("Session request not found.");
        }
        sessionService.cancelSession(requestId);
    }

    @Transactional(readOnly = true)
    public long activeSessionCount() {
        return listRequests().stream()
                .filter(SessionOversightItemView::canCancel)
                .count();
    }

    private SessionOversightItemView toView(SessionRequest request) {
        return new SessionOversightItemView(
                request.getId(),
                request.getMentorName(),
                request.getMenteeEmail(),
                request.getSlotTime(),
                request.getSessionType(),
                toStatusLabel(request.getStatus()),
                toStatusClass(request.getStatus()),
                request.isPaymentCompleted() ? "Paid" : "Not paid",
                request.isPaymentCompleted() ? "statusBadge statusBadge--approved" : "statusBadge statusBadge--neutral",
                request.getCreatedAt().format(SUBMITTED_AT_FORMATTER),
                canCancel(request.getStatus())
        );
    }

    private boolean canCancel(SessionStatus status) {
        return status != SessionStatus.CANCELLED
                && status != SessionStatus.DECLINED
                && status != SessionStatus.COMPLETED;
    }

    private String toStatusLabel(SessionStatus status) {
        return switch (status) {
            case REQUESTED -> "Requested";
            case APPROVED -> "Approved";
            case DECLINED -> "Declined";
            case PAID -> "Paid";
            case CANCELLED -> "Cancelled";
            case COMPLETED -> "Completed";
        };
    }

    private String toStatusClass(SessionStatus status) {
        return switch (status) {
            case REQUESTED -> "statusBadge statusBadge--requested";
            case APPROVED, PAID -> "statusBadge statusBadge--approved";
            case DECLINED -> "statusBadge statusBadge--declined";
            case CANCELLED -> "statusBadge statusBadge--cancelled";
            case COMPLETED -> "statusBadge statusBadge--completed";
        };
    }

    public record SessionOversightItemView(
            Long requestId,
            String mentorName,
            String menteeEmail,
            String slotLabel,
            String sessionType,
            String statusLabel,
            String statusClass,
            String paymentStatusLabel,
            String paymentStatusClass,
            String submittedAtLabel,
            boolean canCancel
    ) {
    }
}
