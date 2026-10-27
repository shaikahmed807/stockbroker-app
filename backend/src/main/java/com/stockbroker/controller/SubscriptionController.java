package com.stockbroker.controller;

import com.stockbroker.dto.SubscriptionDtos.*;
import com.stockbroker.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/plans")
    public List<PlanDto> plans() {
        return subscriptionService.plans();
    }

    @GetMapping("/me")
    public SubscriptionStatusDto status(@AuthenticationPrincipal UserDetails principal) {
        return subscriptionService.status(principal.getUsername());
    }

    @GetMapping("/history")
    public List<SubscriptionDto> history(@AuthenticationPrincipal UserDetails principal) {
        return subscriptionService.history(principal.getUsername());
    }

    @PostMapping("/subscribe")
    public SubscriptionDto subscribe(@AuthenticationPrincipal UserDetails principal,
                                     @Valid @RequestBody SubscribeRequest request) {
        return subscriptionService.subscribe(principal.getUsername(), request.plan());
    }

    @PostMapping("/cancel")
    public SubscriptionStatusDto cancel(@AuthenticationPrincipal UserDetails principal) {
        return subscriptionService.cancel(principal.getUsername());
    }
}
