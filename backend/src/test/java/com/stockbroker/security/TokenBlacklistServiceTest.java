package com.stockbroker.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TokenBlacklistService – logout via Redis")
class TokenBlacklistServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;

    TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        service = new TokenBlacklistService(redis);
    }

    @Test
    void blacklistStoresJtiWithRemainingLifetime() {
        Duration ttl = Duration.ofMinutes(10);
        service.blacklist("abc-123", ttl);
        verify(values).set("jwt:blacklist:abc-123", "1", ttl);
    }

    @Test
    void blacklistIgnoresNullIdOrExpiredToken() {
        service.blacklist(null, Duration.ofMinutes(1));
        service.blacklist("abc", Duration.ZERO);
        service.blacklist("abc", Duration.ofSeconds(-5));
        verifyNoInteractions(values);
    }

    @Test
    void isBlacklistedTrueWhenKeyExists() {
        when(redis.hasKey("jwt:blacklist:abc")).thenReturn(true);
        assertThat(service.isBlacklisted("abc")).isTrue();
    }

    @Test
    void isBlacklistedFalseWhenKeyMissing() {
        when(redis.hasKey("jwt:blacklist:abc")).thenReturn(false);
        assertThat(service.isBlacklisted("abc")).isFalse();
    }

    @Test
    void nullIdIsNeverBlacklisted() {
        assertThat(service.isBlacklisted(null)).isFalse();
        verify(redis, never()).hasKey(anyString());
    }

    @Test
    void failsClosedWhenRedisIsDown() {
        when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertThat(service.isBlacklisted("abc")).isTrue();
    }
}
