package com.stockbroker.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtAuthenticationFilter – Bearer token handling")
class JwtAuthenticationFilterTest {

    @Mock JwtService jwtService;
    @Mock TokenBlacklistService blacklist;
    @Mock CustomUserDetailsService userDetails;
    @Mock Claims claims;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private JwtAuthenticationFilter filter() {
        return new JwtAuthenticationFilter(jwtService, blacklist, userDetails);
    }

    private MockHttpServletRequest request(String authHeader) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/stocks");
        if (authHeader != null) req.addHeader("Authorization", authHeader);
        return req;
    }

    private void validToken() {
        when(jwtService.parse("good")).thenReturn(Optional.of(claims));
        when(claims.getSubject()).thenReturn("jane@example.com");
        when(claims.getId()).thenReturn("jti-1");
        when(userDetails.loadUserByUsername("jane@example.com")).thenReturn(
                User.withUsername("jane@example.com").password("x").authorities("ROLE_USER").build());
    }

    @Test
    void requestWithoutHeaderPassesThroughUnauthenticated() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter().doFilter(request(null), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void nonBearerHeaderIsIgnored() throws Exception {
        filter().doFilter(request("Basic abc"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void validTokenAuthenticatesTheRequest() throws Exception {
        validToken();
        when(blacklist.isBlacklisted("jti-1")).thenReturn(false);
        MockHttpServletRequest req = request("Bearer good");

        filter().doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("jane@example.com");
        assertThat(req.getAttribute(JwtAuthenticationFilter.CLAIMS_ATTRIBUTE)).isSameAs(claims);
    }

    @Test
    void revokedTokenIsNotAccepted() throws Exception {
        validToken();
        when(blacklist.isBlacklisted("jti-1")).thenReturn(true);

        filter().doFilter(request("Bearer good"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetails, never()).loadUserByUsername(anyString());
    }

    @Test
    void invalidSignatureIsNotAccepted() throws Exception {
        when(jwtService.parse("bad")).thenReturn(Optional.empty());
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request("Bearer bad"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).as("chain must continue").isNotNull();
    }

    @Test
    void deletedUserStaysUnauthenticated() throws Exception {
        validToken();
        when(blacklist.isBlacklisted("jti-1")).thenReturn(false);
        when(userDetails.loadUserByUsername("jane@example.com")).thenThrow(new UsernameNotFoundException("gone"));
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request("Bearer good"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }
}
