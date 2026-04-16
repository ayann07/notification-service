package notification_service.messaging;

import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import notification_service.dto.NotificationEvent;
import notification_service.enums.RecipientType;

@ExtendWith(MockitoExtension.class)
class NotificationProducerTest {
    // The producer test is intentionally small: its main job is to verify that we
    // publish to the correct Kafka topic and use the user ID as the message key.

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private NotificationProducer notificationProducer;

    @Test
    void publishEventUsesExpectedTopicAndUserKey() {
        UUID userId = UUID.randomUUID();
        NotificationEvent event = NotificationEvent.builder()
                .producerName("BILLING")
                .recipientType(RecipientType.REGISTERED_USER)
                .userId(userId)
                .eventType("ORDER_SHIPPED")
                .idempotencyKey("idem-1")
                .build();
        ReflectionTestUtils.setField(notificationProducer, "notificationEventsTopic", "notification-events");

        // Act: publish the event.
        notificationProducer.publishEvent(event);

        // Assert: the event should be sent to the notification-events topic, and the
        // key should be the user ID string so partitioning stays stable for that
        // user.
        verify(kafkaTemplate).send("notification-events", userId.toString(), event);
    }

    @Test
    void publishEventFallsBackToIdempotencyKeyForGuestEvents() {
        NotificationEvent event = NotificationEvent.builder()
                .producerName("BILLING")
                .recipientType(RecipientType.GUEST)
                .eventType("ORDER_SHIPPED")
                .correlationId("corr-guest")
                .idempotencyKey("idem-guest")
                .build();
        ReflectionTestUtils.setField(notificationProducer, "notificationEventsTopic", "notification-events");

        notificationProducer.publishEvent(event);

        verify(kafkaTemplate).send("notification-events", "idem-guest", event);
    }
}
