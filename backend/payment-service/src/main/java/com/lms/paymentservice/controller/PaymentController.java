package com.lms.paymentservice.controller;

import com.lms.paymentservice.dto.request.CreatePaymentIntentRequest;
import com.lms.paymentservice.dto.request.RefundRequest;
import com.lms.paymentservice.dto.response.ApiResponse;
import com.lms.paymentservice.dto.response.PaymentIntentResponse;
import com.lms.paymentservice.dto.response.PaymentResponse;
import com.lms.paymentservice.dto.response.RefundResponse;
import com.lms.paymentservice.service.PaymentService;
import com.lms.paymentservice.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    @PostMapping("/intent")
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> createPaymentIntent(
            @Valid @RequestBody CreatePaymentIntentRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.createPaymentIntent(request, UUID.fromString(authentication.getName())),
                "PaymentIntent oluşturuldu"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.getPayment(id, UUID.fromString(authentication.getName())), "Başarılı"));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getMyPayments(
            Authentication authentication,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.getStudentPayments(UUID.fromString(authentication.getName()), pageable), "Başarılı"));
    }

    @PostMapping("/refund")
    public ResponseEntity<ApiResponse<RefundResponse>> requestRefund(
            @Valid @RequestBody RefundRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                refundService.createRefund(request, UUID.fromString(authentication.getName())),
                "İade talebiniz alındı"));
    }
}