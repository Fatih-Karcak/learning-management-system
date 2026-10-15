package com.lms.paymentservice.controller;

import com.lms.paymentservice.config.StripeConfig;
import com.lms.paymentservice.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentService paymentService;
    private final StripeConfig stripeConfig;

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;

        try {
            event = Webhook.constructEvent(
                    payload, sigHeader, stripeConfig.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Geçersiz Stripe webhook imzası");
            return ResponseEntity.badRequest().body("Geçersiz imza");
        }

        log.info("Stripe webhook alındı: {}", event.getType());

        Optional<StripeObject> stripeObject = event.getDataObjectDeserializer()
                .getObject();

        switch (event.getType()) {

            case "payment_intent.succeeded" -> {
                if (stripeObject.isPresent()) {
                    PaymentIntent intent = (PaymentIntent) stripeObject.get();
                    String chargeId = intent.getLatestCharge();
                    paymentService.handlePaymentSucceeded(
                            intent.getId(), chargeId, null);
                }
            }

            case "payment_intent.payment_failed" -> {
                if (stripeObject.isPresent()) {
                    PaymentIntent intent = (PaymentIntent) stripeObject.get();
                    String failureMsg = intent.getLastPaymentError() != null
                            ? intent.getLastPaymentError().getMessage()
                            : "Bilinmeyen hata";
                    paymentService.handlePaymentFailed(intent.getId(), failureMsg);
                }
            }

            default -> log.debug("İşlenmeyen webhook tipi: {}", event.getType());
        }

        return ResponseEntity.ok("OK");
    }
}