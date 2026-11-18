package com.smartpay.fraud.dto;

import com.smartpay.payment.dto.TransactionResponse;

/** ADMIN view: the transaction plus its deterministic risk evidence, if an assessment exists. */
public class AdminInvestigationResponse {

    private final TransactionResponse transaction;
    private final FraudAssessmentResponse assessment;

    public AdminInvestigationResponse(TransactionResponse transaction,
                                      FraudAssessmentResponse assessment) {
        this.transaction = transaction;
        this.assessment = assessment;
    }

    public TransactionResponse getTransaction() { return transaction; }
    public FraudAssessmentResponse getAssessment() { return assessment; }
}
