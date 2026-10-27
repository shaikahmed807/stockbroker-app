package com.stockbroker.controller;

import com.stockbroker.dto.AuthDtos.AuthResponse;
import com.stockbroker.dto.AuthDtos.UserDto;
import com.stockbroker.exception.ApiException;
import com.stockbroker.model.PlanType;
import com.stockbroker.service.AuthService;
import com.stockbroker.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@DisplayName("AuthController – /api/auth endpoints")
class AuthControllerTest extends WebLayerTestConfig {

    @Autowired MockMvc mvc;
    @MockBean AuthService authService;

    private static final UserDto JANE = new UserDto(1L, "Jane Trader", "jane@example.com", "USER",
            PlanType.FREE, false, null, null, null);

    @Test
    void registerReturns201WithToken() throws Exception {
        when(authService.register(any())).thenReturn(new AuthResponse("jwt-token", "Bearer", 86_400_000L, JANE));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Jane Trader","email":"jane@example.com","password":"Secret123"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("jane@example.com"))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    void registerValidatesInput() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"","email":"not-an-email","password":"short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        verifyNoInteractions(authService);
    }

    @Test
    void registerRejectsPasswordWithoutDigit() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Jane","email":"jane@example.com","password":"onlyletters"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value(containsString("letter and one number")));
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        when(authService.register(any())).thenThrow(
                new ApiException(HttpStatus.CONFLICT, "An account with this email already exists"));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Jane","email":"jane@example.com","password":"Secret123"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
    }

    @Test
    void loginIsPublicAndReturnsToken() throws Exception {
        when(authService.login(any())).thenReturn(new AuthResponse("jwt-token", "Bearer", 1000L, JANE));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"Secret123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void wrongPasswordIs401WithGenericMessage() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"nope"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void meRequiresSignIn() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please sign in to continue"));
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void meReturnsCurrentUser() throws Exception {
        when(authService.currentUser("jane@example.com")).thenReturn(JANE);

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Jane Trader"))
                .andExpect(jsonPath("$.plan").value("FREE"));
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void logoutRevokesTokenAndConfirms() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Signed out successfully"));
        verify(authService).logout(isNull());
    }

    @Test
    void logoutWithoutTokenIs401() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
        verify(authService, never()).logout(any());
    }
}
