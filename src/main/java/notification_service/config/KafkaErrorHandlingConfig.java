package notification_service.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaOperations<Object, Object> kafkaOperations,
            @Value("${app.kafka.dlt.suffix:.dlt}") String dltSuffix) {

        return new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (ConsumerRecord<?, ?> record, Exception ex) -> new TopicPartition(record.topic() + dltSuffix,
                        record.partition()));
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(
            DeadLetterPublishingRecoverer recoverer,
            @Value("${app.kafka.retry.interval-ms:1000}") long retryIntervalMs,
            @Value("${app.kafka.retry.max-attempts:3}") long maxAttempts) {

        // Spring Kafka counts retries after the first failed attempt, so "3" means:
        // initial try + 3 retries, then publish to the DLT.
        FixedBackOff backOff = new FixedBackOff(retryIntervalMs, maxAttempts);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
