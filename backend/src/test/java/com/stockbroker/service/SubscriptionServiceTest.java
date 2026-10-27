package com.stockbroker.service;

import com.stockbroker.TestData;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubscriptionService – Premium billing rules")
class SubscriptionServiceTest {

    private static final String TOPIC = "subscription-events";
    private static final String EMAIL = "jane@example.com";

    @Mock SubscriptionRepository subscriptions;
    @Mock UserRepository users;
    @Mock KafkaTemplate<String, Object> kafka;

    SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(subscriptions, users, kafka, TOPIC);
        when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    private void userIs(User u) {
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(u));
    }

    private SubscriptionEvent publishedEvent() {
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq(TOPIC), anyString(), event.capture());
        return (SubscriptionEvent) event.getValue();
    }

    // ---------------- plans & status ----------------

    @Test
    void plansListsFreeMonthlyAndYearlyInRupees() {
        List<PlanDto> plans = service.plans();

        assertThat(plans).extracting(PlanDto::code)
                .containsExactly(PlanType.FREE, PlanType.PREMIUM_MONTHLY, PlanType.PREMIUM_YEARLY);
        assertThat(plans).allSatisfy(p -> assertThat(p.currency()).isEqualTo("INR"));
        assertThat(plans.get(1).price()).isEqualByComparingTo("499");
        assertThat(plans.get(2).price()).isEqualByComparingTo("4999");
    }

    @Test
    void statusOfFreeUser() {
        userIs(TestData.freeUser(1, EMAIL));

        SubscriptionStatusDto status = service.status(EMAIL);

        assertThat(status.plan()).isEqualTo(PlanType.FREE);
        assertThat(status.premium()).isFalse();
        assertThat(status.premiumUntil()).isNull();
        assertThat(status.daysRemaining()).isZero();
    }

    @Test
    void statusOfPremiumUserShowsDaysLeft() {
        userIs(TestData.premiumUser(1, EMAIL, PlanType.PREMIUM_MONTHLY, Duration.ofDays(10).plusHours(1)));

        SubscriptionStatusDto status = service.status(EMAIL);

        assertThat(status.premium()).isTrue();
        assertThat(status.plan()).isEqualTo(PlanType.PREMIUM_MONTHLY);
        assertThat(status.daysRemaining()).isEqualTo(10);
    }

    @Test
    void unknownUserIsUnauthorized() {
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.status(EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------------- subscribe ----------------

    @Test
    void subscribingToFreeOrNothingIsRejected() {
        assertThatThrownBy(() -> service.subscribe(EMAIL, PlanType.FREE))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.subscribe(EMAIL, null)).isInstanceOf(ApiException.class);
        verifyNoInteractions(subscriptions, kafka);
    }

    @Test
    void freeUserSubscribesMonthly() {
        User jane = TestData.freeUser(1, EMAIL);
        userIs(jane);
        Instant before = Instant.now();

        SubscriptionDto dto = service.subscribe(EMAIL, PlanType.PREMIUM_MONTHLY);

        ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions).save(saved.capture());
        Subscription sub = saved.getValue();
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getAmount()).isEqualByComparingTo(new BigDecimal("499"));
        assertThat(sub.getPaymentReference()).startsWith("DEMO-");
        assertThat(sub.getEndDate()).isBetween(before.plus(Duration.ofDays(30)), Instant.now().plus(Duration.ofDays(30)));

        assertThat(jane.getPlan()).isEqualTo(PlanType.PREMIUM_MONTHLY);
        assertThat(jane.isPremiumActive()).isTrue();
        assertThat(dto.plan()).isEqualTo(PlanType.PREMIUM_MONTHLY);

        SubscriptionEvent event = publishedEvent();
        assertThat(event.type()).isEqualTo("ACTIVATED");
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.plan()).isEqualTo(PlanType.PREMIUM_MONTHLY);
        verify(kafka).send(eq(TOPIC), eq("1"), any());
    }

    @Test
    void renewingExtendsFromCurrentEndDateInsteadOfLosingDays() {
        User jane = TestData.premiumUser(1, EMAIL, PlanType.PREMIUM_MONTHLY, Duration.ofDays(5));
        Instant currentEnd = jane.getPremiumUntil();
        userIs(jane);

        service.subscribe(EMAIL, PlanType.PREMIUM_YEARLY);

        assertThat(jane.getPremiumUntil()).isEqualTo(currentEnd.plus(Duration.ofDays(365)));
        assertThat(jane.getPlan()).isEqualTo(PlanType.PREMIUM_YEARLY);
    }

    // ---------------- cancel ----------------

    @Test
    void cancelWithoutPremiumIsRejected() {
        userIs(TestData.freeUser(1, EMAIL));
        when(subscriptions.findByUserIdAndStatus(1L, SubscriptionStatus.ACTIVE)).thenReturn(List.of());

        assertThatThrownBy(() -> service.cancel(EMAIL))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("do not have an active Premium");
        verifyNoInteractions(kafka);
    }

    @Test
    void cancelDowngradesUserAndMarksSubscriptionsCancelled() {
        User jane = TestData.premiumUser(1, EMAIL, PlanType.PREMIUM_MONTHLY, Duration.ofDays(20));
        userIs(jane);
        Subscription active = new Subscription();
        active.setUser(jane);
        active.setPlan(PlanType.PREMIUM_MONTHLY);
        active.setStatus(SubscriptionStatus.ACTIVE);
        when(subscriptions.findByUserIdAndStatus(1L, SubscriptionStatus.ACTIVE)).thenReturn(List.of(active));

        SubscriptionStatusDto status = service.cancel(EMAIL);

        assertThat(active.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(jane.getPlan()).isEqualTo(PlanType.FREE);
        assertThat(jane.getPremiumUntil()).isNull();
        assertThat(status.premium()).isFalse();
        SubscriptionEvent event = publishedEvent();
        assertThat(event.type()).isEqualTo("CANCELLED");
        assertThat(event.plan()).isEqualTo(PlanType.PREMIUM_MONTHLY);
    }

    // ---------------- scheduled expiry ----------------

    @Test
    void expiryJobDowngradesUsersWhosePremiumEnded() {
        User jane = TestData.premiumUser(1, EMAIL, PlanType.PREMIUM_MONTHLY, Duration.ofHours(-1));
        Subscription sub = new Subscription();
        sub.setUser(jane);
        sub.setPlan(PlanType.PREMIUM_MONTHLY);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setEndDate(jane.getPremiumUntil());
        when(subscriptions.findExpired(eq(SubscriptionStatus.ACTIVE), any(Instant.class))).thenReturn(List.of(sub));

        service.expireSubscriptions();

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(jane.getPlan()).isEqualTo(PlanType.FREE);
        assertThat(publishedEvent().type()).isEqualTo("EXPIRED");
    }

    @Test
    void expiryJobKeepsUserPremiumIfAnotherPeriodIsStillRunning() {
        User jane = TestData.premiumUser(1, EMAIL, PlanType.PREMIUM_YEARLY, Duration.ofDays(200));
        Subscription old = new Subscription();
        old.setUser(jane);
        old.setPlan(PlanType.PREMIUM_MONTHLY);
        old.setStatus(SubscriptionStatus.ACTIVE);
        old.setEndDate(Instant.now().minus(Duration.ofDays(1)));
        when(subscriptions.findExpired(eq(SubscriptionStatus.ACTIVE), any(Instant.class))).thenReturn(List.of(old));

        service.expireSubscriptions();

        assertThat(old.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(jane.getPlan()).isEqualTo(PlanType.PREMIUM_YEARLY);
        verifyNoInteractions(kafka);
    }

    @Test
    void kafkaOutageDoesNotBreakSubscribing() {
        when(kafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        userIs(TestData.freeUser(1, EMAIL));

        assertThatCode(() -> service.subscribe(EMAIL, PlanType.PREMIUM_MONTHLY)).doesNotThrowAnyException();
    }
}
