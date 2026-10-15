package com.lms.paymentservice.fraud;

public record FraudResult(
        boolean passed,
        String reason,
        double riskScore
) {
    public static FraudResult safe() {
        return new FraudResult(true, null, 0.0);
    }

    public static FraudResult risky(String reason, double score) {
        return new FraudResult(false, reason, score);
    }

    // İki bağımsız fraud sinyalini birleştirir
    public static FraudResult combine(FraudResult amountCheck, FraudResult velocityCheck) {
        if (!amountCheck.passed()) return amountCheck;
        if (!velocityCheck.passed()) return velocityCheck;

        double combinedScore = Math.max(amountCheck.riskScore(), velocityCheck.riskScore());
        return new FraudResult(true, null, combinedScore);
    }
}