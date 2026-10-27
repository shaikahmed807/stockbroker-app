package com.stockbroker.service;

import com.stockbroker.TestData;
import com.stockbroker.dto.AuthDtos.AuthResponse;
import com.stockbroker.dto.AuthDtos.LoginRequest;
import com.stockbroker.dto.AuthDtos.RegisterRequest;
import com.stockbroker.exception.ApiException;
import com.stockbroker.model.User;
import com.stockbroker.repository.UserRepository;
import com.stockbroker.security.JwtService;
import com.stockbroker.security.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthService – sign up, sign in, sign out")
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock AuthenticationManager authManager;
    @Mock JwtService jwt;
    @Mock TokenBlacklistService blacklist;
    @InjectMocks AuthService service;

    private void tokenWillBeIssued() {
        when(jwt.generateToken(anyString(), anyString())).thenReturn("signed.jwt.token");
        when(jwt.getExpirationMs()).thenReturn(86_400_000L);
    }

    // ---------------- register ----------------

    @Test
    void registerCreatesUserWithHashedPasswordAndReturnsToken() {
        tokenWillBeIssued();
        when(users.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
        when(encoder.encode("Secret123")).thenReturn("$2a$hashed");

        AuthResponse res = service.register(new RegisterRequest("  Jane Trader ", " Jane@Example.com ", "Secret123"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("jane@example.com");
        assertThat(saved.getValue().getFullName()).isEqualTo("Jane Trader");
        assertThat(saved.getValue().getPassword()).isEqualTo("$2a$hashed").isNotEqualTo("Secret123");
        assertThat(saved.getValue().getLastLoginAt()).isNotNull();

        assertThat(res.token()).isEqualTo("signed.jwt.token");
        assertThat(res.tokenType()).isEqualTo("Bearer");
        assertThat(res.expiresInMs()).isEqualTo(86_400_000L);
        assertThat(res.user().email()).isEqualTo("jane@example.com");
        assertThat(res.user().premium()).isFalse();
        verify(jwt).generateToken("jane@example.com", "USER");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmailIgnoreCase("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("Jane", "jane@example.com", "Secret123")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists")
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(users, never()).save(any());
    }

    // ---------------- login ----------------

    @Test
    void loginAuthenticatesAndUpdatesLastLogin() {
        tokenWillBeIssued();
        User jane = TestData.freeUser(1, "jane@example.com");
        when(users.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(jane));

        AuthResponse res = service.login(new LoginRequest("JANE@example.com ", "Secret123"));

        ArgumentCaptor<UsernamePasswordAuthenticationToken> auth =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authManager).authenticate(auth.capture());
        assertThat(auth.getValue().getPrincipal()).isEqualTo("jane@example.com");
        assertThat(auth.getValue().getCredentials()).isEqualTo("Secret123");
        assertThat(jane.getLastLoginAt()).isNotNull();
        assertThat(res.token()).isEqualTo("signed.jwt.token");
    }

    @Test
    void loginWithWrongPasswordFailsWithoutIssuingToken() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> service.login(new LoginRequest("jane@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwt, never()).generateToken(anyString(), anyString());
    }

    @Test
    void loginFailsIfUserVanishedAfterAuthentication() {
        when(users.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("jane@example.com", "Secret123")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------------- logout ----------------

    @Test
    void logoutBlacklistsTokenForItsRemainingLifetime() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("jti-42");
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60_000));

        service.logout(claims);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(blacklist).blacklist(eq("jti-42"), ttl.capture());
        assertThat(ttl.getValue().toMillis()).isBetween(50_000L, 60_000L);
    }

    @Test
    void logoutWithoutTokenDoesNothing() {
        service.logout(null);
        verifyNoInteractions(blacklist);
    }

    // ---------------- current user ----------------

    @Test
    void currentUserReturnsProfile() {
        when(users.findByEmailIgnoreCase("jane@example.com"))
                .thenReturn(Optional.of(TestData.freeUser(7, "jane@example.com")));

        assertThat(service.currentUser("jane@example.com").id()).isEqualTo(7L);
    }

    @Test
    void currentUserUnknownIsUnauthorized() {
        when(users.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.currentUser("ghost@example.com"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
