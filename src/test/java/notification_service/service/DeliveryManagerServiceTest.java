package notification_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import notification_service.delivery.ChannelSender;
import notification_service.enums.DeliveryChannel;
import notification_service.enums.NetworkDeliveryStatus;
import notification_service.model.Notification;
import notification_service.ratelimit.RateLimitingService;
import notification_service.repository.NotificationRepository;

// @ExtendWith tells JUnit (the testing framework) to activate Mockito for this class.
// Without this, the @Mock annotations below would be ignored and remain null.
@ExtendWith(MockitoExtension.class)
class DeliveryManagerServiceTest {

    // @Mock creates a "fake" object. It has the exact same methods as the real
    // ChannelSender,
    // but by default, those methods do absolutely nothing and return null.
    @Mock
    private ChannelSender emailSender;

    // We mock the repository because we don't want to save to a real database
    // during a test.
    // Tests should be fast and not rely on external infrastructure.
    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private RateLimitingService rateLimitingService;

    // @Test tells JUnit, "Hey, this is a test method. Please run it and report if
    // it passes or fails."
    @Test
    void dispatchMarksNotificationRateLimitedWhenChannelBlocked() {

        // --- 1. ARRANGE (Setup your test data and configure your mocks) ---

        Notification notification = notification(); // Call our helper method to get a fresh notification object.

        // We create the real service we are trying to test, injecting our fake
        // dependencies.
        DeliveryManagerService service = new DeliveryManagerService(
                List.of(), notificationRepository, rateLimitingService);

        // "when(...).thenReturn(...)" is how we program our mocks.
        // We are telling the fake rateLimitingService: "If you get asked if the EMAIL
        // channel
        // is allowed for this specific user, you must reply 'false'."
        when(rateLimitingService.isChannelAllowed(notification.getUserId(), DeliveryChannel.EMAIL)).thenReturn(false);

        // --- 2. ACT (Execute the exact method you are trying to test) ---

        service.dispatch(notification);

        // --- 3. ASSERT (Check the results to ensure the code behaved correctly) ---

        // assertEquals(expected, actual). We expect the code to have changed the status
        // to RATE_LIMITED.
        assertEquals(NetworkDeliveryStatus.RATE_LIMITED, notification.getNetworkDeliveryStatus());
        // assertEquals (Testing the State)
        // This checks what the data looks like after the method has run. It compares an
        // expected value against the actual value.

        // If it matches: The test silently passes this line.

        // If it doesn't match: The test crashes instantly. In your terminal or Visual
        // Studio Code, you will get a red error message that looks like:
        // AssertionFailedError: expected: <RATE_LIMITED> but was: <FAILED>

        // "verify" checks if a specific method was called on a mock during the ACT
        // phase.
        // We are verifying that the service successfully called 'save()' on the
        // repository to update the database.
        verify(notificationRepository).save(notification);
    }

    @Test
    void dispatchMarksNotificationFailedWhenNoSenderExists() {

        // --- 1. ARRANGE ---
        Notification notification = notification();
        DeliveryManagerService service = new DeliveryManagerService(
                List.of(), notificationRepository, rateLimitingService);

        // This time, we program the rate limiter to say "true" (allowed).
        when(rateLimitingService.isChannelAllowed(notification.getUserId(), DeliveryChannel.EMAIL)).thenReturn(true);

        // --- 2. ACT ---
        // Notice we passed an empty List.of() into the constructor above.
        // This means there are no senders available to handle the email.
        service.dispatch(notification);

        // --- 3. ASSERT ---
        // Because no sender was found, the service should mark it as FAILED.
        assertEquals(NetworkDeliveryStatus.FAILED, notification.getNetworkDeliveryStatus());
        verify(notificationRepository).save(notification); // Ensure the failure state was saved.
    }

    @Test
    void dispatchMarksNotificationSentWhenSenderSucceeds() {

        // --- 1. ARRANGE ---
        Notification notification = notification();

        // We program the fake emailSender to identify itself as an EMAIL channel
        // sender.
        when(emailSender.getChannelType()).thenReturn(DeliveryChannel.EMAIL);
        when(rateLimitingService.isChannelAllowed(notification.getUserId(), DeliveryChannel.EMAIL)).thenReturn(true);

        // This time, we inject our fake 'emailSender' into the service's list of
        // senders.
        DeliveryManagerService service = new DeliveryManagerService(
                List.of(emailSender), notificationRepository, rateLimitingService);

        // --- 2. ACT ---
        service.dispatch(notification);

        // --- 3. ASSERT ---
        // Verify that the 'send' method on our fake emailSender was actually executed
        // by the service.
        verify(emailSender).send(notification);

        // Verify the status was updated to SENT.
        assertEquals(NetworkDeliveryStatus.SENT, notification.getNetworkDeliveryStatus());
        verify(notificationRepository).save(notification);
    }

    @Test
    void dispatchMarksNotificationFailedWhenSenderThrows() {

        // --- 1. ARRANGE ---
        Notification notification = notification();
        when(emailSender.getChannelType()).thenReturn(DeliveryChannel.EMAIL);
        when(rateLimitingService.isChannelAllowed(notification.getUserId(), DeliveryChannel.EMAIL)).thenReturn(true);

        // 'doThrow' is used to simulate a crash or exception.
        // We are telling the fake emailSender: "When the service tells you to send,
        // throw a RuntimeException."
        doThrow(new RuntimeException("boom")).when(emailSender).send(notification);

        DeliveryManagerService service = new DeliveryManagerService(
                List.of(emailSender), notificationRepository, rateLimitingService);

        // --- 2. ACT ---
        service.dispatch(notification);

        // --- 3. ASSERT ---
        // Even though the sender threw an error, our service should catch it and handle
        // it gracefully
        // by marking the status as FAILED rather than crashing the whole application.
        assertEquals(NetworkDeliveryStatus.FAILED, notification.getNetworkDeliveryStatus());
        verify(notificationRepository).save(notification);
    }

    // This is a private helper method. Instead of copy-pasting the building of a
    // Notification object in every single test, we write it once here and call it.
    private Notification notification() {
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