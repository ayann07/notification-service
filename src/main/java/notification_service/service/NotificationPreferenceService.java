package notification_service.service;

import java.util.HashSet;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification_service.enums.DeliveryChannel;
import notification_service.model.NotificationPreference;
import notification_service.repository.NotificationPreferenceRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationPreference getNotificationPreferences(UUID userId) {
        return getOrCreatePreference(userId);
    }

    @Transactional
    public NotificationPreference toggleChannel(UUID userId, DeliveryChannel deliveryChannel, boolean mute) {
        NotificationPreference pref = getOrCreatePreference(userId);
        if (pref.getMutedChannels() == null)
            pref.setMutedChannels(new HashSet<>());

        if (mute) {
            pref.getMutedChannels().add(deliveryChannel.name());
            log.info("user {} muted channel {}", userId, deliveryChannel);
        } else {
            pref.getMutedChannels().remove(deliveryChannel.name());
            log.info("user {} unmuted channel {}", userId, deliveryChannel);
        }
        return preferenceRepository.save(pref);
    }

    @Transactional
    public NotificationPreference toggleEvent(UUID userId, String event, boolean mute) {
        NotificationPreference pref = getOrCreatePreference(userId);
        if (pref.getMutedEvents() == null)
            pref.setMutedEvents(new HashSet<>());

        if (mute) {
            pref.getMutedEvents().add(event);
            log.info("user {} muted event {}", userId, event);
        } else {
            pref.getMutedEvents().remove(event);
            log.info("user {} unmuted event {}", userId, event);
        }
        return preferenceRepository.save(pref);
    }

    private NotificationPreference getOrCreatePreference(UUID userId) {
        return preferenceRepository.findById(userId).orElseGet(() -> {
            NotificationPreference newPref = new NotificationPreference();
            newPref.setUserId(userId);
            return newPref;
        });
    }
}
