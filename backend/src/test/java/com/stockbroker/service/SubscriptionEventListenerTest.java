package com.stockbroker.service;

import com.stockbroker.dto.SubscriptionDtos.SubscriptionEvent;
import com.stockbroker.model.PlanType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubscriptionEventListener – Kafka consumer side effects")
class SubscriptionEventListenerTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;
    SubscriptionEventListener listener;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        listener = new SubscriptionEventListener(redis);
    }

    private static SubscriptionEvent event(String type) {
        return new SubscriptionEvent(type, 1L, "jane@example.com", PlanType.PREMIUM_MONTHLY,
                new BigDecimal("499"), 1L, 2L);
    }

    @Test
    void countsEventsByTypeInRedis() {
        listener.onEvent(event("ACTIVATED"));
        listener.onEvent(event("CANCELLED"));

        verify(values).increment("stats:subscriptions:activated");
        verify(values).increment("stats:subscriptions:cancelled");
    }

    @Test
    void nullEventIsIgnored() {
        listener.onEvent(null);
        verifyNoInteractions(redis);
    }

    @Test
    void redisFailureIsSwallowed() {
        when(values.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        assertThatCode(() -> listener.onEvent(event("EXPIRED"))).doesNotThrowAnyException();
    }
}
