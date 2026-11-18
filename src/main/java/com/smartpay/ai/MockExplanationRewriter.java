package com.smartpay.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default offline provider: clearly simulated wording, no network call. */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockExplanationRewriter implements ExplanationRewriter {

    @Override
    public Result rewrite(String deterministicExplanation, int score, String riskLevel) {
        String base = deterministicExplanation == null ? "" : deterministicExplanation.trim();
        return new Result("Simulated AI wording: " + base
                + " (Score " + score + "/100, " + riskLevel
                + ". This wording does not change the decision or move money.)", "MOCK");
    }
}
