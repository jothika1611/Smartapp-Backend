package com.smartpay.fraud.controller;

import com.smartpay.fraud.dto.FraudAlertResponse;
import com.smartpay.fraud.dto.FraudRuleRequest;
import com.smartpay.fraud.dto.FraudRuleResponse;
import com.smartpay.fraud.dto.ResolveAlertRequest;
import com.smartpay.fraud.service.FraudAlertAdminService;
import com.smartpay.fraud.service.FraudRuleAdminService;
import com.smartpay.shared.dto.ApiResponse;
import com.smartpay.shared.dto.PageResponse;
import com.smartpay.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN-only alert queue and rule configuration. Route authorization is enforced by SecurityConfig. */
@Tag(name = "Admin fraud operations", description = "Alert queue and configurable scoring rules")
@RestController
@RequestMapping("/api/v1/admin/fraud")
public class AdminFraudController {

    private final FraudAlertAdminService alertService;
    private final FraudRuleAdminService ruleService;

    public AdminFraudController(FraudAlertAdminService alertService,
                                FraudRuleAdminService ruleService) {
        this.alertService = alertService;
        this.ruleService = ruleService;
    }

    @Operation(summary = "Paginated fraud alert queue, optionally filtered by status")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/alerts")
    public ApiResponse<PageResponse<FraudAlertResponse>> alerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(alertService.list(page, size, status));
    }

    @Operation(summary = "Alert detail with deterministic assessment")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/alerts/{alertId}")
    public ApiResponse<FraudAlertResponse> alert(@PathVariable Long alertId) {
        return ApiResponse.ok(alertService.detail(alertId));
    }

    @Operation(summary = "Assign an alert to the authenticated ADMIN")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/alerts/{alertId}/assign")
    public ApiResponse<FraudAlertResponse> assign(@PathVariable Long alertId,
                                                  @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok("Alert assigned", alertService.assign(alertId, user.getId()));
    }

    @Operation(summary = "Resolve an alert with mandatory notes")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/alerts/{alertId}/resolve")
    public ApiResponse<FraudAlertResponse> resolve(@PathVariable Long alertId,
                                                   @Valid @RequestBody ResolveAlertRequest request,
                                                   @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok("Alert resolved",
                alertService.resolve(alertId, request.getResolutionNotes(), user.getId()));
    }

    @Operation(summary = "List all scoring rules, including disabled ones")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/rules")
    public ApiResponse<PageResponse<FraudRuleResponse>> rules(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(ruleService.list(page, size));
    }

    @Operation(summary = "Create a new scoring rule")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/rules")
    public ApiResponse<FraudRuleResponse> createRule(@Valid @RequestBody FraudRuleRequest request) {
        return ApiResponse.ok("Rule created", ruleService.create(request));
    }

    @Operation(summary = "Update a rule; its code is immutable")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/rules/{ruleId}")
    public ApiResponse<FraudRuleResponse> updateRule(@PathVariable Long ruleId,
                                                     @Valid @RequestBody FraudRuleRequest request) {
        return ApiResponse.ok("Rule updated", ruleService.update(ruleId, request));
    }

    @Operation(summary = "Disable a rule without deleting historical hits")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/rules/{ruleId}")
    public ApiResponse<FraudRuleResponse> disableRule(@PathVariable Long ruleId) {
        return ApiResponse.ok("Rule disabled", ruleService.disable(ruleId));
    }
}
