package com.stockbroker.repository;

import com.stockbroker.model.Subscription;
import com.stockbroker.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Subscription> findByUserIdAndStatus(Long userId, SubscriptionStatus status);

    @Query("select s from Subscription s join fetch s.user where s.status = :status and s.endDate < :now")
    List<Subscription> findExpired(SubscriptionStatus status, Instant now);
}
