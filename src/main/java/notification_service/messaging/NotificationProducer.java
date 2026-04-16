package notification_service.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification_service.dto.NotificationEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    @Value("${app.kafka.topics.notification-events:notification-events}")
    private String notificationEventsTopic;

    public void publishEvent(NotificationEvent event) {
        String messageKey = resolveMessageKey(event);
        log.info("Publishing event into Kafka. topic={} key={} correlationId={} eventType={}",
                notificationEventsTopic,
                messageKey,
                event.getCorrelationId(),
                event.getEventType());
        kafkaTemplate.send(notificationEventsTopic, messageKey, event);
    }

    private String resolveMessageKey(NotificationEvent event) {
        if (event.getUserId() != null) {
            return event.getUserId().toString();
        }
        if (event.getIdempotencyKey() != null && !event.getIdempotencyKey().isBlank()) {
            return event.getIdempotencyKey();
        }
        if (event.getCorrelationId() != null && !event.getCorrelationId().isBlank()) {
            return event.getCorrelationId();
        }
        return "notification-event";
    }
}
