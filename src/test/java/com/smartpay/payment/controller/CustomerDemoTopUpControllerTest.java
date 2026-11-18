package com.smartpay.payment.controller;

import com.smartpay.payment.dto.CustomerDemoTopUpRequest;
import com.smartpay.payment.dto.DemoFundingRequest;
import com.smartpay.payment.dto.FundingResponse;
import com.smartpay.payment.service.DemoFundingService;
import com.smartpay.shared.dto.ApiResponse;
import com.smartpay.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerDemoTopUpControllerTest {

    @Mock
    private DemoFundingService demoFundingService;

    @InjectMocks
    private CustomerDemoTopUpController controller;

    @Test
    void creditsTheAuthenticatedUserOnlyAndLabelsTheResponseAsSimulated() {
        FundingResponse expected = new FundingResponse(
                "SIM-1", 7L, new BigDecimal("250.00"), "INR", "COMPLETED", new BigDecimal("750.00"));
        when(demoFundingService.fund(eq(7L), eq("topup-key-1"), any(DemoFundingRequest.class)))
                .thenReturn(expected);

        CustomerDemoTopUpRequest request = new CustomerDemoTopUpRequest();
        request.setAmount(new BigDecimal("250.00"));

        ApiResponse<FundingResponse> response = controller.topUp(principal(7L), "topup-key-1", request);

        ArgumentCaptor<DemoFundingRequest> captor = ArgumentCaptor.forClass(DemoFundingRequest.class);
        verify(demoFundingService).fund(eq(7L), eq("topup-key-1"), captor.capture());
        DemoFundingRequest funding = captor.getValue();
        assertThat(funding.getUserId()).isEqualTo(7L);
        assertThat(funding.getAmount()).isEqualByComparingTo("250.00");
        assertThat(funding.getDescription()).contains("Simulated", "customer");

        assertThat(response.isSimulated()).isTrue();
        assertThat(response.getMessage()).contains("No real money");
        assertThat(response.getData()).isEqualTo(expected);
    }

    private CurrentUser principal(Long id) {
        return new CurrentUser() {
            @Override public Long getId() { return id; }
            @Override public String getEmail() { return "customer@smartpay.local"; }
            @Override public boolean isAdmin() { return false; }
        };
    }
}
