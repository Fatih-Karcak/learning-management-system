package com.lms.paymentservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PaymentServiceApplicationTests {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment environment;

    @org.springframework.beans.factory.annotation.Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Test
    @SuppressWarnings("unchecked")
    void outboxPayloadKeepsEventFieldsWithConfiguredKafkaSerializer() throws Exception {
        Class<?> serializerType = Class.forName(environment.getRequiredProperty("spring.kafka.producer.value-serializer"));
        try (var serializer = (org.apache.kafka.common.serialization.Serializer<Object>)
                serializerType.getConstructor().newInstance()) {
            var id = java.util.UUID.randomUUID();
            var event = new com.lms.paymentservice.event.PaymentCompletedEvent(id, java.util.UUID.randomUUID(),
                    java.util.UUID.randomUUID(), "Course", null, "Instructor", java.math.BigDecimal.TEN);
            byte[] wire = serializer.serialize("payment-completed", objectMapper.valueToTree(event));
            var decoded = objectMapper.readValue(wire, com.lms.paymentservice.event.PaymentCompletedEvent.class);
            org.junit.jupiter.api.Assertions.assertEquals(event, decoded);
        }
    }

    @Test
    void contextLoads() {
    }

}
