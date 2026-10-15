package com.lms.paymentservice.fraud;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
public class FraudDetectionService {

    /**
     * Tutar bazlı kural kontrolü.
     * ARTIK GERÇEK KURS FİYATIYLA çağrılıyor (placeholder değil).
     */
    public FraudResult checkAmountRisk(UUID studentId, BigDecimal amount, String currency) {
        log.info("Tutar bazlı fraud kontrolü - StudentId: {}, Amount: {} {}",
                studentId, amount, currency);

        if (amount.compareTo(new BigDecimal("10000")) > 0) {
            return FraudResult.risky("Yüksek tutarlı işlem şüpheli", 0.85);
        }

        return FraudResult.safe();
    }

    /**
     * Öğrencinin son zamandaki işlem sıklığı kontrolü.
     * Bu kontrol TUTARDAN BAĞIMSIZ çalışabilir — gerçek paralel iş burada.
     * Gerçek projede: son 1 saatte kaç ödeme denemesi yapıldı, IP/cihaz kontrolü vb.
     */
    public FraudResult checkVelocity(UUID studentId) {
        log.info("Velocity (işlem sıklığı) kontrolü - StudentId: {}", studentId);

        // Demo: gerçekte Redis/DB'den son N dakikadaki deneme sayısı çekilir
        int recentAttempts = simulateRecentAttemptLookup(studentId);

        if (recentAttempts > 5) {
            return FraudResult.risky(
                    "Kısa sürede çok fazla ödeme denemesi: " + recentAttempts, 0.9);
        }

        return FraudResult.safe();
    }

    private int simulateRecentAttemptLookup(UUID studentId) {
        // Placeholder — gerçek projede Redis counter olacak
        return 0;
    }
}