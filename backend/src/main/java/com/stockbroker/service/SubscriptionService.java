package com.stockbroker.service;

import com.stockbroker.dto.SubscriptionDtos.PlanDto;
import com.stockbroker.dto.SubscriptionDtos.SubscriptionDto;
import com.stockbroker.dto.SubscriptionDtos.SubscriptionEvent;
import com.stockbroker.dto.SubscriptionDtos.SubscriptionStatusDto;
import com.stockbroker.exception.ApiException;
import com.stockbroker.model.PlanType;
import com.stockbroker.model.Subscription;
import com.stockbroker.model.SubscriptionStatus;
import com.stockbroker.model.User;
import com.stockbroker.repository.SubscriptionRepository;
import com.stockbroker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, UserRepository userRepository,
                               KafkaTemplate<String, Object> kafkaTemplate,
                               @Value("${app.kafka.topics.subscription-events}") String topic) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public List<PlanDto> plans() {
        return Arrays.stream(PlanType.values()).map(PlanDto::from).toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionStatusDto status(String email) {
        User user = user(email);
        if (!user.isPremiumActive()) {
            return new SubscriptionStatusDto(PlanType.FREE, false, null, 0);
        }
        long days = Math.max(0, Duration.between(Instant.now(), user.getPremiumUntil()).toDays());
        return new SubscriptionStatusDto(user.getPlan(), true, user.getPremiumUntil(), days);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDto> history(String email) {
        return subscriptionRepository.findByUserIdOrderByCreatedAtDesc(user(email).getId())
                .stream().map(SubscriptionDto::from).toList();
    }

    /**
     * Activates (or extends) a Premium plan.
     *
     * NOTE: payment is simulated in this project. Plug a real gateway (Razorpay / Stripe) in here:
     * create an order, let the frontend complete checkout, verify the gateway signature, and only
     * then call this logic with the gateway's payment id.
     */
    @Transactional
    public SubscriptionDto subscribe(String email, PlanType plan) {
        if (plan == null || !plan.isPremium()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a Premium plan to subscribe");
        }
        User user = user(email);
        Instant now = Instant.now();

        // If already premium, extend from the current end date instead of losing remaining days.
        Instant base = user.isPremiumActive() ? user.getPremiumUntil() : now;
        Instant end = base.plus(Duration.ofDays(plan.getDurationDays()));

        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setPlan(plan);
        sub.setAmount(plan.getPrice());
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartDate(now);
        sub.setEndDate(end);
        sub.setPaymentReference("DEMO-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase());
        subscriptionRepository.save(sub);

        user.setPlan(plan);
        user.setPremiumUntil(end);

        publish("ACTIVATED", user, plan, end);
        return SubscriptionDto.from(sub);
    }

    @Transactional
    public SubscriptionStatusDto cancel(String email) {
        User user = user(email);
        List<Subscription> active = subscriptionRepository.findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE);
        if (active.isEmpty() && !user.isPremiumActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You do not have an active Premium subscription");
        }
        active.forEach(s -> s.setStatus(SubscriptionStatus.CANCELLED));
        PlanType previous = user.getPlan();
        user.setPlan(PlanType.FREE);
        user.setPremiumUntil(null);

        publish("CANCELLED", user, previous, Instant.now());
        return new SubscriptionStatusDto(PlanType.FREE, false, null, 0);
    }

    /** Runs every 15 minutes and downgrades users whose Premium period has ended. */
    @Scheduled(fixedDelay = 15 * 60 * 1000L, initialDelay = 60 * 1000L)
    @Transactional
    public void expireSubscriptions() {
        List<Subscription> expired = subscriptionRepository.findExpired(SubscriptionStatus.ACTIVE, Instant.now());
        for (Subscription s : expired) {
            s.setStatus(SubscriptionStatus.EXPIRED);
            User u = s.getUser();
            if (!u.isPremiumActive()) {
                u.setPlan(PlanType.FREE);
                u.setPremiumUntil(null);
                publish("EXPIRED", u, s.getPlan(), s.getEndDate());
            }
        }
        if (!expired.isEmpty()) log.info("Expired {} subscriptions", expired.size());
    }

    private void publish(String type, User user, PlanType plan, Instant validUntil) {
        SubscriptionEvent event = new SubscriptionEvent(type, user.getId(), user.getEmail(), plan,
                plan.getPrice(), validUntil.toEpochMilli(), System.currentTimeMillis());
        kafkaTemplate.send(topic, String.valueOf(user.getId()), event).whenComplete((r, ex) -> {
            if (ex != null) log.warn("Could not publish subscription event {}: {}", type, ex.getMessage());
        });
    }

    private User user(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
