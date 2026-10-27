package com.stockbroker.controller;

import com.stockbroker.security.CustomUserDetailsService;
import com.stockbroker.security.JwtService;
import com.stockbroker.security.TokenBlacklistService;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Base class for @WebMvcTest slices. Subclasses @Import the real SecurityConfig (JWT filter + rules);
 * the beans it needs that would talk to Redis or MySQL are mocked here.
 */
abstract class WebLayerTestConfig {

    @MockBean protected JwtService jwtService;
    @MockBean protected TokenBlacklistService tokenBlacklistService;
    @MockBean protected CustomUserDetailsService customUserDetailsService;
}
