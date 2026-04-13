package notification_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import notification_service.delivery.ChannelSender;
import notification_service.enums.DeliveryChannel;
import notification_service.enums.NetworkDeliveryStatus;
import notification_service.model.Notification;
import notification_service.ratelimit.RateLimitingService;
import notification_service.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class DeliveryManagerServiceTest {
    // DeliveryManagerService decides what to do with a prepared Notification:
    // block it, route it to the correct sender, and update final delivery status.

    @Mock
    // Fake channel sender used to simulate successful or failing delivery.
    private ChannelSender emailSender;

    @Mock
    // Fake repository so we can verify status persistence without a database.
    private NotificationRepository notificationRepository;

    @Mock
    // Fake rate limiter so each test can choose whether delivery is allowed.
    private RateLimitingService rateLimitingService;

    @Test
    void dispatchMarksNotificationRateLimitedWhenChannelBlocked() {
        Notification notification = notification();
        // We create the service manually here because it builds a sender registry
        // from the list we pass into the constructor.
        DeliveryManagerService service = new DeliveryManagerService(List.of(), notificationRepository, rateLimitingService);
        when(rateLimitingService.isChannelAllowed(notification.getUserId(), DeliveryChannel.EMAIL)).thenReturn(false);

        // Act: try to dispatch a notification on a blocked channel.
        service.dispatch(notification);

        // Assert: status should become RATE_LIMITED and be saved.
        assertEquals(NetworkDeliveryStatus.RATE_LIMITED, notification.getNetworkDeliveryStatus());
        verify(notificationRepository).save(notification);
    }

    @Test
    void dispatchMarksNotificationFailedWhenNoSenderExists() {
        Notification notification = notification();
        DeliveryManagerService service = new DeliveryManagerService(List.of(), notificationRepository, rateLimitingService);
        when(rateLimitingService.isChannelAllowed(notification.getUserId(), DeliveryChannel.EMAIL)).thenReturn(true);

        // There is no sender registered for EMAIL in this test, so dispatch should
        // fail gracefully.
        service.dispatch(notification);

        assertEquals(NetworkDeliveryStatus.FAILED, notification.getNetworkDeliveryStatus());
        verify(notificationRepository).save(notification);
    }

    @Test
    void dispatchMarksNotificationSentWhenSenderSucceeds() {
        Notification notification = notification();
        when(emailSender.getChannelType()).thenReturn(DeliveryChannel.EMAIL);
        when(rateLimitingService.isChannelAllowed(notification.getUserId(), DeliveryChannel.EMAIL)).thenReturn(true);

        // Register one sender that claims it handles the EMAIL channel.
        DeliveryManagerService service = new DeliveryManagerService(
                List.of(emailSender), notificationRepository, rateLimitingService);

        service.dispatch(notification);

        // verify(...) means "this method must have been called".
        verify(emailSender).send(notification);
        assertEquals(NetworkDeliveryStatus.SENT, notification.getNetworkDeliveryStatus());
        verify(notificationRepository).save(notification);
    }

    @Test
    void dispatchMarksNotificationFailedWhenSenderThrows() {
        Notification notification = notification();
        when(emailSender.getChannelType()).thenReturn(DeliveryChannel.EMAIL);
        when(rateLimitingService.isChannelAllowed(notification.getUserId(), DeliveryChannel.EMAIL)).thenReturn(true);

        // doThrow(...).when(...) is Mockito's way to simulate an exception from a
        // void method.
        doThrow(new RuntimeException("boom")).when(emailSender).send(notification);
        DeliveryManagerService service = new DeliveryManagerService(
                List.of(emailSender), notificationRepository, rateLimitingService);

        service.dispatch(notification);

        assertEquals(NetworkDeliveryStatus.FAILED, notification.getNetworkDeliveryStatus());
        verify(notificationRepository).save(notification);
    }

    private Notification notification() {
        // Helper that builds a reusable notification object for all dispatch tests.
        return Notification.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .deliveryChannel(DeliveryChannel.EMAIL)
                .eventType("ORDER_SHIPPED")
                .idempotencyKey("idem-1")
                .title("Order shipped")
                .message("Your order is on the way")
                .build();
    }
}
