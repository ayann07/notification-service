package notification_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import notification_service.enums.DeliveryChannel;
import notification_service.model.NotificationPreference;
import notification_service.repository.NotificationPreferenceRepository;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {
    // This class tests the per-user notification preference rules.
    // The service either creates a default preference or toggles muted events and
    // channels.

    @Mock
    // Fake repository used to simulate stored preferences.
    private NotificationPreferenceRepository preferenceRepository;

    @InjectMocks
    // Real service under test.
    private NotificationPreferenceService preferenceService;

    @Test
    void getNotificationPreferencesCreatesTransientPreferenceWhenMissing() {
        UUID userId = UUID.randomUUID();
        when(preferenceRepository.findById(userId)).thenReturn(Optional.empty());

        // Act: ask for preferences for a user who has nothing stored yet.
        NotificationPreference preference = preferenceService.getNotificationPreferences(userId);

        // Assert: the service creates an in-memory default preference object.
        assertEquals(userId, preference.getUserId());
        assertTrue(preference.getMutedChannels().isEmpty());
        assertTrue(preference.getMutedEvents().isEmpty());
    }

    @Test
    void toggleChannelAddsMutedChannel() {
        UUID userId = UUID.randomUUID();
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(userId);
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(preference));

        // thenAnswer(invocation -> invocation.getArgument(0)) means:
        // "when save(...) is called, just give me back the exact same object."
        when(preferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreference updated = preferenceService.toggleChannel(userId, DeliveryChannel.EMAIL, true);

        assertTrue(updated.getMutedChannels().contains("EMAIL"));
    }

    @Test
    void toggleChannelRemovesMutedChannelAndRepairsNullSet() {
        UUID userId = UUID.randomUUID();
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(userId);

        // We set the collection to null on purpose to verify the service repairs it
        // safely before removing anything.
        preference.setMutedChannels(null);
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(preference));
        when(preferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreference updated = preferenceService.toggleChannel(userId, DeliveryChannel.SMS, false);

        assertTrue(updated.getMutedChannels().isEmpty());
    }

    @Test
    void toggleEventAddsMutedEvent() {
        UUID userId = UUID.randomUUID();
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(userId);
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(preference));
        when(preferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreference updated = preferenceService.toggleEvent(userId, "ORDER_SHIPPED", true);

        assertTrue(updated.getMutedEvents().contains("ORDER_SHIPPED"));
    }

    @Test
    void toggleEventRemovesMutedEvent() {
        UUID userId = UUID.randomUUID();
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(userId);

        // HashSet is mutable, which matches what the real code expects when it calls
        // remove(...).
        preference.setMutedEvents(new HashSet<>(Set.of("ORDER_SHIPPED")));
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(preference));
        when(preferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreference updated = preferenceService.toggleEvent(userId, "ORDER_SHIPPED", false);

        assertTrue(updated.getMutedEvents().isEmpty());
    }
}
