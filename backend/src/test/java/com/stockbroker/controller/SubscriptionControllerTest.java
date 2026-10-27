package com.stockbroker.controller;

import com.stockbroker.dto.SubscriptionDtos.PlanDto;
import com.stockbroker.dto.SubscriptionDtos.SubscriptionDto;
import com.stockbroker.dto.SubscriptionDtos.SubscriptionStatusDto;
import com.stockbroker.exception.ApiException;
import com.stockbroker.model.PlanType;
import com.stockbroker.model.SubscriptionStatus;
import com.stockbroker.service.SubscriptionService;
import com.stockbroker.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
@Import(SecurityConfig.class)
@DisplayName("SubscriptionController – /api/subscriptions endpoints")
class SubscriptionControllerTest extends WebLayerTestConfig {

    @Autowired MockMvc mvc;
    @MockBean SubscriptionService subscriptionService;

    @Test
    void plansArePublic() throws Exception {
        when(subscriptionService.plans()).thenReturn(Arrays.stream(PlanType.values()).map(PlanDto::from).toList());

        mvc.perform(get("/api/subscriptions/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[1].code").value("PREMIUM_MONTHLY"))
                .andExpect(jsonPath("$[1].price").value(499));
    }

    @Test
    void statusRequiresSignIn() throws Exception {
        mvc.perform(get("/api/subscriptions/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/subscriptions/subscribe")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void statusOfSignedInUser() throws Exception {
        when(subscriptionService.status("jane@example.com"))
                .thenReturn(new SubscriptionStatusDto(PlanType.FREE, false, null, 0));

        mvc.perform(get("/api/subscriptions/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.premium").value(false));
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void subscribeActivatesPlan() throws Exception {
        when(subscriptionService.subscribe("jane@example.com", PlanType.PREMIUM_MONTHLY)).thenReturn(
                new SubscriptionDto(5L, PlanType.PREMIUM_MONTHLY, new BigDecimal("499"), SubscriptionStatus.ACTIVE,
                        Instant.now(), Instant.now(), "DEMO-ABC", Instant.now()));

        mvc.perform(post("/api/subscriptions/subscribe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"PREMIUM_MONTHLY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.paymentReference").value("DEMO-ABC"));
    }

    @Test
    @WithMockUser
    void subscribeWithoutPlanIs400() throws Exception {
        mvc.perform(post("/api/subscriptions/subscribe").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.plan").value("Plan is required"));
        verify(subscriptionService, never()).subscribe(any(), any());
    }

    @Test
    @WithMockUser
    void subscribeWithUnknownPlanIs400() throws Exception {
        mvc.perform(post("/api/subscriptions/subscribe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"GOLD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void historyListsPayments() throws Exception {
        when(subscriptionService.history("jane@example.com")).thenReturn(List.of(
                new SubscriptionDto(1L, PlanType.PREMIUM_YEARLY, new BigDecimal("4999"), SubscriptionStatus.CANCELLED,
                        null, null, "DEMO-1", null)));

        mvc.perform(get("/api/subscriptions/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void cancelWithoutPremiumIs400() throws Exception {
        when(subscriptionService.cancel("jane@example.com")).thenThrow(
                new ApiException(HttpStatus.BAD_REQUEST, "You do not have an active Premium subscription"));

        mvc.perform(post("/api/subscriptions/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You do not have an active Premium subscription"));
    }
}
