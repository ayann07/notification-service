package notification_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import notification_service.cache.IdempotencyCache;
import notification_service.cache.UnreadCounterCache;
import notification_service.dto.GuestUserDetails;
import notification_service.dto.NotificationEvent;
import notification_service.enums.DeliveryChannel;
import notification_service.enums.RecipientType;
import notification_service.exceptions.InvalidRequestException;
import notification_service.exceptions.ResourceNotFoundException;
import notification_service.model.Notification;
import notification_service.model.NotificationPreference;
import notification_service.model.NotificationTemplate;
import notification_service.model.User;
import notification_service.repository.NotificationPreferenceRepository;
import notification_service.repository.NotificationRepository;
import notification_service.repository.NotificationTemplateRepository;
import notification_service.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class NotificationProcessingServiceTest {
    // NotificationProcessingService is the orchestration core of the app.
    // These tests focus on the important branches: early exits, validation,
    // preference checks, template hydration, persistence, and dispatching.

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationTemplateRepository templateRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Mock
    private DeliveryManagerService deliveryManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IdempotencyCache idempotencyCache;

    @Mock
    private UnreadCounterCache unreadCounterCache;

    @InjectMocks
    private NotificationProcessingService notificationProcessingService;

    @Test
    void processReturnsEarlyForDuplicateEvent() {
        NotificationEvent event = registeredEvent();
        when(idempotencyCache.isDuplicate("idem-1")).thenReturn(true);

        // Duplicate events should stop immediately so we do not create the same
        // notification twice.
        notificationProcessingService.process(event);

        verify(templateRepository, never()).findAllByEventTypeAndIsActiveTrue(any());
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void processThrowsWhenGuestDetailsAreMissingForGuestRecipient() {
        NotificationEvent event = NotificationEvent.builder()
                .producerName("BILLING")
                .recipientType(RecipientType.GUEST)
                .eventType("ORDER_SHIPPED")
                .idempotencyKey("idem-1")
                .build();
        when(idempotencyCache.isDuplicate("idem-1")).thenReturn(false);

        assertThrows(InvalidRequestException.class, () -> notificationProcessingService.process(event));
    }

    @Test
    void processThrowsWhenRegisteredRecipientIsMissingUserId() {
        NotificationEvent event = NotificationEvent.builder()
                .producerName("BILLING")
                .recipientType(RecipientType.REGISTERED_USER)
                .eventType("ORDER_SHIPPED")
                .idempotencyKey("idem-1")
                .build();
        when(idempotencyCache.isDuplicate("idem-1")).thenReturn(false);

        assertThrows(InvalidRequestException.class, () -> notificationProcessingService.process(event));
    }

    @Test
    void processThrowsWhenRegisteredUserDoesNotExist() {
        NotificationEvent event = registeredEvent();
        when(idempotencyCache.isDuplicate("idem-1")).thenReturn(false);
        when(userRepository.findById(event.getUserId())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> notificationProcessingService.process(event));
    }

    @Test
    void processReturnsEarlyWhenEventIsMuted() {
        NotificationEvent event = registeredEvent();
        NotificationPreference preference = new NotificationPreference();
        preference.setMutedEvents(Set.of("ORDER_SHIPPED"));

        when(idempotencyCache.isDuplicate("idem-1")).thenReturn(false);
        when(userRepository.findById(event.getUserId())).thenReturn(Optional.of(user(event.getUserId())));
        when(templateRepository.findAllByEventTypeAndIsActiveTrue("ORDER_SHIPPED")).thenReturn(List.of(emailTemplate()));
        when(preferenceRepository.findById(event.getUserId())).thenReturn(Optional.of(preference));

        // If the user muted this event type, the service should drop it before
        // creating any notification row.
        notificationProcessingService.process(event);

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(deliveryManager, never()).dispatch(any(Notification.class));
    }

    @Test
    void processSkipsMutedChannels() {
        NotificationEvent event = registeredEvent();
        NotificationPreference preference = new NotificationPreference();
        preference.setMutedChannels(Set.of("EMAIL"));

        when(idempotencyCache.isDuplicate("idem-1")).thenReturn(false);
        when(userRepository.findById(event.getUserId())).thenReturn(Optional.of(user(event.getUserId())));
        when(templateRepository.findAllByEventTypeAndIsActiveTrue("ORDER_SHIPPED")).thenReturn(List.of(emailTemplate()));
        when(preferenceRepository.findById(event.getUserId())).thenReturn(Optional.of(preference));

        // Here the event itself is allowed, but the EMAIL channel is muted, so the
        // per-channel notification should be skipped.
        notificationProcessingService.process(event);

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(deliveryManager, never()).dispatch(any(Notification.class));
    }

    @Test
    void processSkipsGuestPushNotifications() {
        NotificationEvent event = guestEvent();
        NotificationTemplate pushTemplate = NotificationTemplate.builder()
                .id(UUID.randomUUID())
                .eventType("ORDER_SHIPPED")
                .title("Hi {firstName}")
                .body("Body for {firstName}")
                .deliveryChannel(DeliveryChannel.PUSH_NOTIFICATION)
                .defaultPriority((short) 3)
                .build();

        when(idempotencyCache.isDuplicate("idem-guest")).thenReturn(false);
        when(templateRepository.findAllByEventTypeAndIsActiveTrue("ORDER_SHIPPED")).thenReturn(List.of(pushTemplate));

        // Guests do not have stored device tokens, so PUSH notifications are skipped.
        notificationProcessingService.process(event);

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(deliveryManager, never()).dispatch(any(Notification.class));
    }

    @Test
    void processCreatesNotificationHydratesTemplateAndDispatches() {
        NotificationEvent event = registeredEvent();
        NotificationTemplate template = emailTemplate();
        when(idempotencyCache.isDuplicate("idem-1")).thenReturn(false);
        when(userRepository.findById(event.getUserId())).thenReturn(Optional.of(user(event.getUserId())));
        when(templateRepository.findAllByEventTypeAndIsActiveTrue("ORDER_SHIPPED")).thenReturn(List.of(template));
        when(preferenceRepository.findById(event.getUserId())).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationProcessingService.process(event);

        // We capture the saved notification so we can inspect exactly what the
        // orchestration layer built before handing it to the delivery manager.
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertEquals("Hi Ayan", saved.getTitle());
        assertEquals("Order 42 for Ayan is on the way", saved.getMessage());
        assertEquals(DeliveryChannel.EMAIL, saved.getDeliveryChannel());
        assertEquals("ayan@example.com", saved.getRecipientEmail());
        verify(unreadCounterCache).increment(event.getUserId());
        verify(deliveryManager).dispatch(saved);
    }

    private NotificationEvent registeredEvent() {
        // Standard registered-user event reused across multiple tests.
        return NotificationEvent.builder()
                .producerName("BILLING")
                .recipientType(RecipientType.REGISTERED_USER)
                .userId(UUID.randomUUID())
                .eventType("ORDER_SHIPPED")
                .correlationId("corr-1")
                .idempotencyKey("idem-1")
                .metadata(Map.of("orderId", 42))
                .build();
    }

    private NotificationEvent guestEvent() {
        // Standard guest event reused across tests that exercise the guest flow.
        return NotificationEvent.builder()
                .producerName("BILLING")
                .recipientType(RecipientType.GUEST)
                .eventType("ORDER_SHIPPED")
                .correlationId("corr-guest")
                .idempotencyKey("idem-guest")
                .guestUserDetails(GuestUserDetails.builder()
                        .firstName("Guest")
                        .email("guest@example.com")
                        .phoneNumber("9999999999")
                        .build())
                .build();
    }

    private User user(UUID userId) {
        // Fake user returned by the mocked repository when the service resolves a
        // registered recipient.
        return User.builder()
                .id(userId)
                .firstName("Ayan")
                .lastName("Roy")
                .email("ayan@example.com")
                .phoneNumber("9999999999")
                .build();
    }

    private NotificationTemplate emailTemplate() {
        // The placeholders in this template are important because the hydration
        // test checks that metadata + user details are merged correctly.
        return NotificationTemplate.builder()
                .id(UUID.randomUUID())
                .eventType("ORDER_SHIPPED")
                .title("Hi {firstName}")
                .body("Order {orderId} for {firstName} is on the way")
                .deliveryChannel(DeliveryChannel.EMAIL)
                .defaultPriority((short) 3)
                .build();
    }
}
