package com.smartpay.fraud.controller;

import com.smartpay.fraud.dto.FraudAssessmentResponse;
import com.smartpay.fraud.service.FraudAssessmentQueryService;
import com.smartpay.shared.dto.ApiResponse;
import com.smartpay.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Fraud assessments", description = "Deterministic rule explanations for a transaction")
@RestController
@RequestMapping("/api/v1/transactions")
public class FraudAssessmentController {

    private final FraudAssessmentQueryService queryService;

    public FraudAssessmentController(FraudAssessmentQueryService queryService) {
        this.queryService = queryService;
    }

    @Operation(summary = "Risk score, deterministic explanation and rule hits for a participant/ADMIN")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{reference}/assessment")
    public ApiResponse<FraudAssessmentResponse> assessment(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable String reference) {
        return ApiResponse.ok(queryService.get(user.getId(), user.isAdmin(), reference));
    }
}
