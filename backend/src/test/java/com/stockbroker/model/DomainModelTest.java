package com.stockbroker.model;

import com.stockbroker.TestData;
import com.stockbroker.dto.AuthDtos.UserDto;
import com.stockbroker.dto.SubscriptionDtos.PlanDto;
import com.stockbroker.dto.SubscriptionDtos.SubscriptionDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Domain model – plans, users and DTO mapping")
class DomainModelTest {

    @Test
    void planPricesAndDurations() {
        assertThat(PlanType.FREE.getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(PlanType.PREMIUM_MONTHLY.getPrice()).isEqualByComparingTo("499");
        assertThat(PlanType.PREMIUM_MONTHLY.getDurationDays()).isEqualTo(30);
        assertThat(PlanType.PREMIUM_YEARLY.getPrice()).isEqualByComparingTo("4999");
        assertThat(PlanType.PREMIUM_YEARLY.getDurationDays()).isEqualTo(365);
    }

    @Test
    void yearlyPlanIsCheaperThanTwelveMonths() {
        BigDecimal twelveMonths = PlanType.PREMIUM_MONTHLY.getPrice().multiply(BigDecimal.valueOf(12));
        assertThat(PlanType.PREMIUM_YEARLY.getPrice()).isLessThan(twelveMonths);
    }

    @Test
    void onlyPaidPlansArePremium() {
        assertThat(PlanType.FREE.isPremium()).isFalse();
        assertThat(PlanType.PREMIUM_MONTHLY.isPremium()).isTrue();
        assertThat(PlanType.PREMIUM_YEARLY.isPremium()).isTrue();
        assertThat(PlanType.PREMIUM_MONTHLY.getFeatures()).isNotEmpty();
    }

    @Test
    void newUserDefaultsToFreeUserRole() {
        User u = new User();
        assertThat(u.getRole()).isEqualTo(Role.USER);
        assertThat(u.getPlan()).isEqualTo(PlanType.FREE);
        assertThat(u.isPremiumActive()).isFalse();
    }

    @Test
    void premiumIsActiveOnlyUntilItsEndDate() {
        assertThat(TestData.premiumUser(1, "a@b.com", PlanType.PREMIUM_MONTHLY, Duration.ofDays(1)).isPremiumActive()).isTrue();
        assertThat(TestData.premiumUser(1, "a@b.com", PlanType.PREMIUM_MONTHLY, Duration.ofSeconds(-1)).isPremiumActive()).isFalse();

        User noEndDate = TestData.freeUser(1, "a@b.com");
        noEndDate.setPlan(PlanType.PREMIUM_YEARLY);
        assertThat(noEndDate.isPremiumActive()).isFalse();
    }

    @Test
    void userDtoHidesExpiredPremium() {
        User expired = TestData.premiumUser(9, "a@b.com", PlanType.PREMIUM_MONTHLY, Duration.ofDays(-2));

        UserDto dto = UserDto.from(expired);

        assertThat(dto.premium()).isFalse();
        assertThat(dto.plan()).isEqualTo(PlanType.FREE);
        assertThat(dto.premiumUntil()).isNull();
        assertThat(dto.role()).isEqualTo("USER");
    }

    @Test
    void userDtoShowsActivePremium() {
        User active = TestData.premiumUser(9, "a@b.com", PlanType.PREMIUM_YEARLY, Duration.ofDays(100));

        UserDto dto = UserDto.from(active);

        assertThat(dto.premium()).isTrue();
        assertThat(dto.plan()).isEqualTo(PlanType.PREMIUM_YEARLY);
        assertThat(dto.premiumUntil()).isEqualTo(active.getPremiumUntil());
    }

    @Test
    void userDtoNeverExposesPassword() {
        assertThat(UserDto.class.getRecordComponents())
                .extracting(c -> c.getName())
                .doesNotContain("password");
    }

    @Test
    void planDtoMapping() {
        PlanDto dto = PlanDto.from(PlanType.PREMIUM_MONTHLY);
        assertThat(dto.name()).isEqualTo("Premium Monthly");
        assertThat(dto.currency()).isEqualTo("INR");
        assertThat(dto.durationDays()).isEqualTo(30);
    }

    @Test
    void subscriptionDtoMapping() {
        Subscription s = new Subscription();
        s.setPlan(PlanType.PREMIUM_MONTHLY);
        s.setAmount(new BigDecimal("499"));
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setStartDate(Instant.EPOCH);
        s.setEndDate(Instant.EPOCH.plus(Duration.ofDays(30)));
        s.setPaymentReference("DEMO-1");

        SubscriptionDto dto = SubscriptionDto.from(s);

        assertThat(dto.plan()).isEqualTo(PlanType.PREMIUM_MONTHLY);
        assertThat(dto.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(dto.paymentReference()).isEqualTo("DEMO-1");
        assertThat(dto.endDate()).isEqualTo(Instant.EPOCH.plus(Duration.ofDays(30)));
    }

    @Test
    void stockConstructorStartsAtBasePrice() {
        Stock s = TestData.tcs();
        assertThat(s.getLastPrice()).isEqualTo(s.getBasePrice());
        assertThat(s.getExchange()).isEqualTo("NSE");
    }
}
