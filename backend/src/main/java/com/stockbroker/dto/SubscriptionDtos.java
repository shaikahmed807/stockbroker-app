package com.stockbroker.dto;

import com.stockbroker.model.PlanType;
import com.stockbroker.model.Subscription;
import com.stockbroker.model.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SubscriptionDtos {

    private SubscriptionDtos() { }

    public record PlanDto(PlanType code, String name, BigDecimal price, String currency,
                          int durationDays, List<String> features) {
        public static PlanDto from(PlanType p) {
            return new PlanDto(p, p.getDisplayName(), p.getPrice(), "INR", p.getDurationDays(), p.getFeatures());
        }
    }

    public record SubscribeRequest(@NotNull(message = "Plan is required") PlanType plan) { }

    public record SubscriptionDto(Long id, PlanType plan, BigDecimal amount, SubscriptionStatus status,
                                  Instant startDate, Instant endDate, String paymentReference,
                                  Instant createdAt) {
        public static SubscriptionDto from(Subscription s) {
            return new SubscriptionDto(s.getId(), s.getPlan(), s.getAmount(), s.getStatus(),
                    s.getStartDate(), s.getEndDate(), s.getPaymentReference(), s.getCreatedAt());
        }
    }

    public record SubscriptionStatusDto(PlanType plan, boolean premium, Instant premiumUntil,
                                        long daysRemaining) { }

    /** Published to Kafka topic "subscription-events". */
    public record SubscriptionEvent(String type, Long userId, String email, PlanType plan,
                                    BigDecimal amount, long validUntilEpochMs, long occurredAtEpochMs) { }
}
