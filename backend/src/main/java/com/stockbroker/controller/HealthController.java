package com.stockbroker.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Quick dependency check for MySQL and Redis (requires sign-in). */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final DataSource dataSource;
    private final StringRedisTemplate redis;

    public HealthController(DataSource dataSource, StringRedisTemplate redis) {
        this.dataSource = dataSource;
        this.redis = redis;
    }

    @GetMapping
    public Map<String, String> health() {
        Map<String, String> status = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection()) {
            status.put("mysql", c.isValid(2) ? "UP" : "DOWN");
        } catch (Exception e) {
            status.put("mysql", "DOWN");
        }
        try {
            String pong = redis.execute((org.springframework.data.redis.core.RedisCallback<String>) conn -> conn.ping());
            status.put("redis", "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN");
        } catch (Exception e) {
            status.put("redis", "DOWN");
        }
        return status;
    }
}
