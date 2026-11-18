package com.smartpay.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiExplanationServiceTest {

    @Mock
    private ExplanationRewriter rewriter;

    @InjectMocks
    private AiExplanationService service;

    @Test
    void returnsProviderWording() {
        when(rewriter.rewrite(anyString(), anyInt(), anyString()))
                .thenReturn(new ExplanationRewriter.Result("Clear wording", "OPENAI"));

        ExplanationRewriter.Result result = service.rewrite("Risk 30 (MEDIUM)", 30, "MEDIUM");

        assertThat(result.text()).isEqualTo("Clear wording");
        assertThat(result.source()).isEqualTo("OPENAI");
    }

    @Test
    void providerFailureUsesDeterministicFallback() {
        when(rewriter.rewrite(anyString(), anyInt(), anyString()))
                .thenThrow(new RuntimeException("model unavailable"));

        ExplanationRewriter.Result result = service.rewrite("Risk 30 (MEDIUM)", 30, "MEDIUM");

        assertThat(result.text()).contains("Simulated AI wording");
        assertThat(result.text()).contains("no decision changed");
        assertThat(result.source()).isEqualTo("MOCK_FALLBACK");
    }
}
