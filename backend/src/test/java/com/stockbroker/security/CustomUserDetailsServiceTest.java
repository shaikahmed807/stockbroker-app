package com.stockbroker.security;

import com.stockbroker.TestData;
import com.stockbroker.model.PlanType;
import com.stockbroker.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService – roles for Spring Security")
class CustomUserDetailsServiceTest {

    @Mock UserRepository users;
    @InjectMocks CustomUserDetailsService service;

    @Test
    void freeUserGetsOnlyUserRole() {
        when(users.findByEmailIgnoreCase("jane@example.com"))
                .thenReturn(Optional.of(TestData.freeUser(1, "jane@example.com")));

        UserDetails details = service.loadUserByUsername("jane@example.com");

        assertThat(details.getUsername()).isEqualTo("jane@example.com");
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void activePremiumUserAlsoGetsPremiumRole() {
        when(users.findByEmailIgnoreCase("p@example.com")).thenReturn(Optional.of(
                TestData.premiumUser(2, "p@example.com", PlanType.PREMIUM_MONTHLY, Duration.ofDays(3))));

        assertThat(service.loadUserByUsername("p@example.com").getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_PREMIUM");
    }

    @Test
    void expiredPremiumDoesNotGetPremiumRole() {
        when(users.findByEmailIgnoreCase("old@example.com")).thenReturn(Optional.of(
                TestData.premiumUser(3, "old@example.com", PlanType.PREMIUM_MONTHLY, Duration.ofDays(-1))));

        assertThat(service.loadUserByUsername("old@example.com").getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void unknownUserThrows() {
        when(users.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadUserByUsername("ghost@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
