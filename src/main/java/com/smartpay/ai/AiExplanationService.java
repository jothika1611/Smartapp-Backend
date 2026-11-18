package com.smartpay.ai;

import org.springframework.stereotype.Service;

/** Small seam used by fraud persistence so a provider failure can never fail a payment. */
@Service
public class AiExplanationService {

    private final ExplanationRewriter rewriter;

    public AiExplanationService(ExplanationRewriter rewriter) {
        this.rewriter = rewriter;
    }

    public ExplanationRewriter.Result rewrite(String deterministicExplanation, int score, String riskLevel) {
        try {
            ExplanationRewriter.Result result = rewriter.rewrite(deterministicExplanation, score, riskLevel);
            if (result == null || result.text() == null || result.text().isBlank()) {
                throw new IllegalStateException("Rewriter returned no text");
            }
            return result;
        } catch (RuntimeException exception) {
            String fallback = "Simulated AI wording: " + deterministicExplanation
                    + " (Score " + score + "/100, " + riskLevel
                    + ". AI unavailable; no decision changed.)";
            return new ExplanationRewriter.Result(fallback, "MOCK_FALLBACK");
        }
    }
}
