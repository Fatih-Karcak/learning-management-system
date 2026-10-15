package com.lms.paymentservice.mapper;

import com.lms.paymentservice.domain.entity.Payment;
import com.lms.paymentservice.domain.entity.Refund;
import com.lms.paymentservice.dto.response.PaymentResponse;
import com.lms.paymentservice.dto.response.RefundResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PaymentMapper {

    PaymentResponse toResponse(Payment payment);

    @Mapping(source = "payment.id", target = "paymentId")
    RefundResponse toRefundResponse(Refund refund);
}