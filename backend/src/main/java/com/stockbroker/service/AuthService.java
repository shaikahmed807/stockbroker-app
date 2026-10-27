package com.stockbroker.service;

import com.stockbroker.dto.AuthDtos.AuthResponse;
import com.stockbroker.dto.AuthDtos.LoginRequest;
import com.stockbroker.dto.AuthDtos.RegisterRequest;
import com.stockbroker.dto.AuthDtos.UserDto;
import com.stockbroker.exception.ApiException;
import com.stockbroker.model.User;
import com.stockbroker.repository.UserRepository;
import com.stockbroker.security.JwtService;
import com.stockbroker.security.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService,
                       TokenBlacklistService blacklistService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
    }

    /** Sign up: creates the account and signs the user in straight away. */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User user = new User();
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        return issueToken(user);
    }

    /** Sign in / log in. */
    @Transactional
    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.password()));
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        user.setLastLoginAt(Instant.now());
        return issueToken(user);
    }

    /** Sign out / log out: revokes the current token until it expires. */
    public void logout(Claims claims) {
        if (claims == null) return;
        long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
        blacklistService.blacklist(claims.getId(), Duration.ofMillis(remainingMs));
    }

    @Transactional(readOnly = true)
    public UserDto currentUser(String email) {
        return UserDto.from(findUser(email));
    }

    public User findUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private AuthResponse issueToken(User user) {
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMs(), UserDto.from(user));
    }
}
