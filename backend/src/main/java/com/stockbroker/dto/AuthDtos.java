package com.stockbroker.dto;

import com.stockbroker.model.PlanType;
import com.stockbroker.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Request/response bodies for sign up, sign in and sign out. */
public final class AuthDtos {

    private AuthDtos() { }

    public record RegisterRequest(
            @NotBlank(message = "Full name is required")
            @Size(max = 100) String fullName,

            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email") String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 64, message = "Password must be 8-64 characters")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                    message = "Password must contain at least one letter and one number")
            String password
    ) { }

    public record LoginRequest(
            @NotBlank(message = "Email is required") @Email String email,
            @NotBlank(message = "Password is required") String password
    ) { }

    public record AuthResponse(String token, String tokenType, long expiresInMs, UserDto user) { }

    public record UserDto(
            Long id,
            String fullName,
            String email,
            String role,
            PlanType plan,
            boolean premium,
            Instant premiumUntil,
            Instant createdAt,
            Instant lastLoginAt
    ) {
        public static UserDto from(User u) {
            boolean active = u.isPremiumActive();
            return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getRole().name(),
                    active ? u.getPlan() : PlanType.FREE, active, active ? u.getPremiumUntil() : null,
                    u.getCreatedAt(), u.getLastLoginAt());
        }
    }

    public record MessageResponse(String message) { }
}
