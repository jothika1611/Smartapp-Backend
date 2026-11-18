package com.smartpay.ai;

/** Rewrites fraud wording only. It never changes score, risk level, or payment state. */
public interface ExplanationRewriter {

    /** Returns cleared-up wording plus the source label stored beside the deterministic text. */
    Result rewrite(String deterministicExplanation, int score, String riskLevel);

    record Result(String text, String source) { }
}
