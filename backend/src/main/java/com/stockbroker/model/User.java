package com.stockbroker.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlanType plan = PlanType.FREE;

    private Instant premiumUntil;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant lastLoginAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    /** True while the user holds a paid plan that has not yet expired. */
    public boolean isPremiumActive() {
        return plan != null && plan.isPremium()
                && premiumUntil != null && premiumUntil.isAfter(Instant.now());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public PlanType getPlan() { return plan; }
    public void setPlan(PlanType plan) { this.plan = plan; }
    public Instant getPremiumUntil() { return premiumUntil; }
    public void setPremiumUntil(Instant premiumUntil) { this.premiumUntil = premiumUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
