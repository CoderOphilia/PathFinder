package com.pathfinder.admin.service;

import com.pathfinder.session.web.DemoSessionStore;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class AdminSessionOversightService {

    private static final DateTimeFormatter SUBMITTED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a", Locale.ENGLISH);

    private final DemoSessionStore sessionStore;

    public AdminSessionOversightService(DemoSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public List<SessionOversightItemView> listRequests() {
        return sessionStore.listRequestsForMentorQueue().stream()
                .map(this::toView)
                .toList();
    }

    public void cancelRequest(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new IllegalArgumentException("Session request not found.");
        }
        sessionStore.cancelRequest(requestId, DemoSessionStore.CancellationActor.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Only active session requests can be cancelled."));
    }

    public long activeSessionCount() {
        return listRequests().stream()
                .filter(SessionOversightItemView::canCancel)
                .count();
    }

    private SessionOversightItemView toView(DemoSessionStore.SessionRequestView request) {
        return new SessionOversightItemView(
                request.requestId(),
                request.mentorName(),
                request.slotLabel(),
                request.sessionType(),
                toStatusLabel(request.status()),
                toStatusClass(request.status()),
                toPaymentStatusLabel(request.paymentStatus()),
                toPaymentStatusClass(request.paymentStatus()),
                request.submittedAt().format(SUBMITTED_AT_FORMATTER),
                !isTerminal(request.status())
        );
    }

    private boolean isTerminal(DemoSessionStore.SessionStatus status) {
        return status == DemoSessionStore.SessionStatus.DECLINED
                || status == DemoSessionStore.SessionStatus.CANCELLED
                || status == DemoSessionStore.SessionStatus.EXPIRED
                || status == DemoSessionStore.SessionStatus.COMPLETED;
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
            case PARTIAL_REFUND -> "Partial refund";
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

    public record SessionOversightItemView(
            String requestId,
            String mentorName,
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
