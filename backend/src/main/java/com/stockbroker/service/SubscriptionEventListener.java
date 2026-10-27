package com.stockbroker.service;

import com.stockbroker.dto.SubscriptionDtos.SubscriptionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Kafka CONSUMER for "subscription-events".
 * Decoupled side effects of a subscription change live here: e.g. sending a receipt email,
 * notifying analytics, or granting access in other services. This demo logs the event and
 * keeps simple counters in Redis.
 */
@Service
public class SubscriptionEventListener {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEventListener.class);

    private final StringRedisTemplate redis;

    public SubscriptionEventListener(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.subscription-events}",
            groupId = "stockbroker-subscriptions",
            properties = {"spring.json.value.default.type=com.stockbroker.dto.SubscriptionDtos$SubscriptionEvent"})
    public void onEvent(SubscriptionEvent event) {
        if (event == null) return;
        log.info("Subscription event: {} user={} plan={} amount={}",
                event.type(), event.email(), event.plan(), event.amount());
        try {
            redis.opsForValue().increment("stats:subscriptions:" + event.type().toLowerCase());
        } catch (Exception e) {
            log.warn("Could not update subscription stats: {}", e.getMessage());
        }
    }
}
