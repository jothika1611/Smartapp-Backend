package com.smartpay.fraud.dto;

import java.util.List;

/** Read-only view of the deterministic assessment. AI text stays separate and optional. */
public class FraudAssessmentResponse {

    private final String reference;
    private final int score;
    private final String riskLevel;
    private final String deterministicExplanation;
    private final String aiExplanation;
    private final String aiSource;
    private final List<RuleHitResponse> hits;

    public FraudAssessmentResponse(String reference, int score, String riskLevel,
                                   String deterministicExplanation, String aiExplanation,
                                   String aiSource, List<RuleHitResponse> hits) {
        this.reference = reference;
        this.score = score;
        this.riskLevel = riskLevel;
        this.deterministicExplanation = deterministicExplanation;
        this.aiExplanation = aiExplanation;
        this.aiSource = aiSource;
        this.hits = hits;
    }

    public String getReference() { return reference; }
    public int getScore() { return score; }
    public String getRiskLevel() { return riskLevel; }
    public String getDeterministicExplanation() { return deterministicExplanation; }
    public String getAiExplanation() { return aiExplanation; }
    public String getAiSource() { return aiSource; }
    public List<RuleHitResponse> getHits() { return hits; }

    public static class RuleHitResponse {
        private final String ruleCode;
        private final int points;
        private final String detail;

        public RuleHitResponse(String ruleCode, int points, String detail) {
            this.ruleCode = ruleCode;
            this.points = points;
            this.detail = detail;
        }

        public String getRuleCode() { return ruleCode; }
        public int getPoints() { return points; }
        public String getDetail() { return detail; }
    }
}
