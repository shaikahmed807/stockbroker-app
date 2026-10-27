package com.stockbroker.repository;

import com.stockbroker.model.PlanType;
import com.stockbroker.model.PriceTick;
import com.stockbroker.model.Stock;
import com.stockbroker.model.Subscription;
import com.stockbroker.model.SubscriptionStatus;
import com.stockbroker.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the Hibernate mappings and custom queries against an in-memory H2 database. */
@DataJpaTest
@DisplayName("Repositories – Hibernate mappings and queries (H2)")
class RepositoryIntegrationTest {

    @Autowired TestEntityManager em;
    @Autowired UserRepository users;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired PriceTickRepository ticks;
    @Autowired StockRepository stocks;

    private User user(String email) {
        User u = new User();
        u.setFullName("Test User");
        u.setEmail(email);
        u.setPassword("hash");
        return em.persistAndFlush(u);
    }

    private Subscription subscription(User u, SubscriptionStatus status, Instant end) {
        Subscription s = new Subscription();
        s.setUser(u);
        s.setPlan(PlanType.PREMIUM_MONTHLY);
        s.setAmount(new BigDecimal("499.00"));
        s.setStatus(status);
        s.setStartDate(end.minus(Duration.ofDays(30)));
        s.setEndDate(end);
        s.setPaymentReference("DEMO-TEST");
        return em.persistAndFlush(s);
    }

    @Test
    void emailLookupIsCaseInsensitive() {
        user("jane@example.com");

        assertThat(users.findByEmailIgnoreCase("JANE@Example.COM")).isPresent();
        assertThat(users.existsByEmailIgnoreCase("Jane@example.com")).isTrue();
        assertThat(users.existsByEmailIgnoreCase("other@example.com")).isFalse();
    }

    @Test
    void createdAtIsSetAutomatically() {
        assertThat(user("a@example.com").getCreatedAt()).isNotNull();
    }

    @Test
    void findExpiredReturnsOnlyActiveSubscriptionsPastTheirEndDate() {
        User u = user("jane@example.com");
        Instant now = Instant.now();
        Subscription expired = subscription(u, SubscriptionStatus.ACTIVE, now.minus(Duration.ofDays(1)));
        subscription(u, SubscriptionStatus.ACTIVE, now.plus(Duration.ofDays(10)));       // still running
        subscription(u, SubscriptionStatus.CANCELLED, now.minus(Duration.ofDays(5)));    // already closed
        em.clear();

        List<Subscription> result = subscriptions.findExpired(SubscriptionStatus.ACTIVE, now);

        assertThat(result).extracting(Subscription::getId).containsExactly(expired.getId());
        assertThat(result.get(0).getUser().getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    void historyIsNewestFirst() {
        User u = user("jane@example.com");
        subscription(u, SubscriptionStatus.EXPIRED, Instant.now().minus(Duration.ofDays(40)));
        subscription(u, SubscriptionStatus.ACTIVE, Instant.now().plus(Duration.ofDays(20)));

        List<Subscription> history = subscriptions.findByUserIdOrderByCreatedAtDesc(u.getId());

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getCreatedAt()).isAfterOrEqualTo(history.get(1).getCreatedAt());
        assertThat(subscriptions.findByUserIdAndStatus(u.getId(), SubscriptionStatus.ACTIVE)).hasSize(1);
    }

    @Test
    void priceTicksArePagedNewestFirst() {
        for (int i = 0; i < 5; i++) {
            ticks.save(new PriceTick("TCS", 100 + i, 10, Instant.ofEpochMilli(1000L * i)));
        }
        ticks.save(new PriceTick("INFY", 50, 10, Instant.ofEpochMilli(9999)));

        List<PriceTick> latest = ticks.findBySymbolOrderByTickTimeDesc("TCS", PageRequest.of(0, 3));

        assertThat(latest).extracting(PriceTick::getPrice).containsExactly(104.0, 103.0, 102.0);
    }

    @Test
    void stocksAreKeyedBySymbol() {
        stocks.save(new Stock("TCS", "Tata Consultancy Services Ltd", "Information Technology", 4100));

        assertThat(stocks.findById("TCS")).get().extracting(Stock::getCompanyName)
                .isEqualTo("Tata Consultancy Services Ltd");
        assertThat(stocks.existsById("WIPRO")).isFalse();
    }
}
