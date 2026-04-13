package notification_service.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import notification_service.dto.NotificationEvent;
import notification_service.enums.RecipientType;
import notification_service.ratelimit.RateLimitingService;
import notification_service.service.NotificationProcessingService;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {
    // The Kafka consumer mostly coordinates rate limiting and delegation.
    // These tests check when it should stop early and when it should hand work to
    // NotificationProcessingService.

    @Mock
    private NotificationProcessingService processingService;

    @Mock
    private RateLimitingService rateLimitingService;

    @InjectMocks
    private NotificationConsumer notificationConsumer;

    @Test
    void consumeNotificationEventStopsWhenProducerIsRateLimited() {
        NotificationEvent event = event();
        when(rateLimitingService.isProducerAllowed("BILLING")).thenReturn(false);

        // If the producer itself is blocked, the message should never reach the
        // processing layer.
        notificationConsumer.consumeNotificationEvent(event);

        verify(processingService, never()).process(event);
    }

    @Test
    void consumeNotificationEventStopsWhenUserEventIsRateLimited() {
        NotificationEvent event = event();
        when(rateLimitingService.isProducerAllowed("BILLING")).thenReturn(true);
        when(rateLimitingService.isUserEventAllowed(event.getUserId(), event.getEventType())).thenReturn(false);

        notificationConsumer.consumeNotificationEvent(event);

        verify(processingService, never()).process(event);
    }

    @Test
    void consumeNotificationEventDelegatesToProcessingServiceWhenAllowed() {
        NotificationEvent event = event();
        when(rateLimitingService.isProducerAllowed("BILLING")).thenReturn(true);
        when(rateLimitingService.isUserEventAllowed(event.getUserId(), event.getEventType())).thenReturn(true);

        notificationConsumer.consumeNotificationEvent(event);

        verify(processingService).process(event);
    }

    @Test
    void consumeNotificationEventSwallowsUnexpectedProcessingErrors() {
        NotificationEvent event = event();
        when(rateLimitingService.isProducerAllowed("BILLING")).thenReturn(true);
        when(rateLimitingService.isUserEventAllowed(event.getUserId(), event.getEventType())).thenReturn(true);
        doThrow(new RuntimeException("boom")).when(processingService).process(event);

        // Kafka listeners should not crash the whole consumer loop because one
        // message failed unexpectedly.
        assertDoesNotThrow(() -> notificationConsumer.consumeNotificationEvent(event));
    }

    private NotificationEvent event() {
        // Reusable sample event for consumer tests.
        return NotificationEvent.builder()
                .producerName("BILLING")
                .recipientType(RecipientType.REGISTERED_USER)
                .userId(UUID.randomUUID())
                .eventType("ORDER_SHIPPED")
                .correlationId("corr-1")
                .idempotencyKey("idem-1")
                .build();
    }
}
