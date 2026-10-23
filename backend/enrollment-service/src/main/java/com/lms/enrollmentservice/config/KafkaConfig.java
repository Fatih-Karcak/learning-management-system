package com.lms.enrollmentservice.config;

import com.lms.enrollmentservice.event.PaymentCompletedEvent;
import com.lms.enrollmentservice.event.RefundCompletedEvent;
import java.util.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.support.serializer.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        config.put(JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        // Normal olaylar JSON olarak, deserialize edilemeyen
        // Kafka payload'ları ham byte[] olarak DLT'ye yazılabilir.
        Map<Class<?>, Serializer<?>> serializers = new LinkedHashMap<>();

        serializers.put(byte[].class, new ByteArraySerializer());

        serializers.put(Object.class, new JacksonJsonSerializer<>());

        var valueSerializer = new DelegatingByTypeSerializer(serializers, true);

        return new DefaultKafkaProducerFactory<>(config, new StringSerializer(), valueSerializer);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    private <T> ConsumerFactory<String, T> consumerFactory(Class<T> type) {
        Map<String, Object> config = new HashMap<>();

        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        config.put(ConsumerConfig.GROUP_ID_CONFIG, "enrollment-service");

        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        var json = new JacksonJsonDeserializer<>(type, false);

        var errorHandling = new ErrorHandlingDeserializer<T>(json);

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), errorHandling);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, (record, exception) ->
            new TopicPartition(record.topic() + ".DLT", record.partition())
        );

        return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 4L));
    }

    @Bean("kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedEvent> paymentCompletedFactory(
        DefaultErrorHandler kafkaErrorHandler
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedEvent>();

        factory.setConsumerFactory(consumerFactory(PaymentCompletedEvent.class));

        factory.setCommonErrorHandler(kafkaErrorHandler);

        return factory;
    }

    @Bean("refundKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, RefundCompletedEvent> refundFactory(
        DefaultErrorHandler kafkaErrorHandler
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, RefundCompletedEvent>();

        factory.setConsumerFactory(consumerFactory(RefundCompletedEvent.class));

        factory.setCommonErrorHandler(kafkaErrorHandler);

        return factory;
    }

    @Bean("courseNoticeFactory")
    public ConcurrentKafkaListenerContainerFactory<
        String,
        com.lms.enrollmentservice.event.PlatformNotice
    > courseNoticeFactory(DefaultErrorHandler kafkaErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<
            String,
            com.lms.enrollmentservice.event.PlatformNotice
        >();
        factory.setConsumerFactory(consumerFactory(com.lms.enrollmentservice.event.PlatformNotice.class));
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
