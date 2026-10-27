package com.stockbroker.controller;

import com.stockbroker.dto.AuthDtos.*;
import com.stockbroker.security.JwtAuthenticationFilter;
import com.stockbroker.service.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Sign up */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** Sign in / Log in */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Sign out / Log out */
    @PostMapping("/logout")
    public MessageResponse logout(HttpServletRequest request) {
        Claims claims = (Claims) request.getAttribute(JwtAuthenticationFilter.CLAIMS_ATTRIBUTE);
        authService.logout(claims);
        SecurityContextHolder.clearContext();
        return new MessageResponse("Signed out successfully");
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal UserDetails principal) {
        return authService.currentUser(principal.getUsername());
    }
}
