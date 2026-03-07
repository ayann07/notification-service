package notification_service.service;

import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification_service.dto.NotificationEvent;
import notification_service.enums.DeliveryChannel;
import notification_service.enums.NetworkDeliveryStatus;
import notification_service.enums.RecipientType;
import notification_service.enums.UserReadStatus;
import notification_service.model.Notification;
import notification_service.model.NotificationPreference;
import notification_service.model.NotificationTemplate;
import notification_service.model.User;
import notification_service.repository.NotificationPreferenceRepository;
import notification_service.repository.NotificationRepository;
import notification_service.repository.NotificationTemplateRepository;
import notification_service.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationProcessingService {

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final DeliveryManagerService deliveryManager;
    private final UserRepository userRepository;

    @Transactional
    public void process(NotificationEvent event) {
        log.info("Processing event with correlationId:{}", event.getCorrelationId());
        ContactInfo contactInfo = resolveContactInfo(event);
        NotificationTemplate template = getActiveTemplateByEventType(event.getEventType());
        NotificationPreference preference = resolvePreferences(event.getUserId());
        if (isEventMuted(preference, event.getEventType())) {
            log.info("User {} muted event {}. Dropping notification.", event.getUserId(), event.getEventType());
            return;
        }
        Map<String, Object> enrichedMetadata = new HashMap<>();
        if (event.getMetadata() != null)
            enrichedMetadata.putAll(event.getMetadata());
        enrichedMetadata.put("firstName", contactInfo.firstName());
        if (contactInfo.lastName() != null) {
            enrichedMetadata.put("lastName", contactInfo.lastName());
        }

        String finalMessage = hydrateTemplate(template.getBody(), enrichedMetadata);
        List<String> targetChannels = template.getDefaultChannels();
        for (String channel : targetChannels) {
            processSingleChannel(channel, event, contactInfo, template, preference, finalMessage, enrichedMetadata);
        }
    }

    private ContactInfo resolveContactInfo(NotificationEvent event) {
        if (event.getRecipientType() == RecipientType.GUEST) {
            log.info("Processing GUEST user via explicit GuestUserDetails object.");
            return new ContactInfo(event.getGuestUserDetails().getFirstName(),
                    event.getGuestUserDetails().getLastName(), event.getGuestUserDetails().getEmail(),
                    event.getGuestUserDetails().getPhoneNumber());
        } else if (event.getRecipientType() == RecipientType.REGISTERED_USER) {
            log.info("Processing REGISTERED_USER:", event.getUserId());
            User user = userRepository.findById(event.getUserId()).orElseThrow(() -> new IllegalArgumentException(
                    "user not found with the given id: user not found in the local db"));
            return new ContactInfo(user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhoneNumber());
        } else {
            throw new IllegalArgumentException("Unknown recipient type");
        }

    }

    private NotificationTemplate getActiveTemplateByEventType(String eventType) {
        return templateRepository.findByEventTypeAndIsActiveTrue(eventType)
                .orElseThrow(() -> new IllegalArgumentException("no active template found with given eventType"));
    }

    private NotificationPreference resolvePreferences(UUID userId) {
        if (userId == null) {
            return new NotificationPreference(); // Guests have no preferences
        }
        return preferenceRepository.findById(userId).orElse(new NotificationPreference());
    }

    private boolean isEventMuted(NotificationPreference preference, String eventType) {
        return preference.getMutedEvents() != null && preference.getMutedEvents().contains(eventType);
    }

    private void processSingleChannel(String channel, NotificationEvent event, ContactInfo contactInfo,
            NotificationTemplate template, NotificationPreference preference,
            String finalMessage, Map<String, Object> enrichedMetadata) {

        if (preference.getMutedChannels() != null && preference.getMutedChannels().contains(channel)) {
            log.info("User {} muted channel {}. Skipping this channel.", event.getUserId(), channel);
            return;
        }

        String channelIdempotencyKey = event.getIdempotencyKey() != null ? event.getIdempotencyKey() + "-" + channel
                : null;
        if (channelIdempotencyKey != null && notificationRepository.existsByIdempotencyKey(channelIdempotencyKey)) {
            log.info("Duplicate event detected, skipping channel {} for key: {}", channel, channelIdempotencyKey);
            return;
        }

        Notification notification = Notification.builder()
                .userId(event.getUserId())
                .recipientEmail(contactInfo.email())
                .recipientPhone(contactInfo.phone())
                .templateId(template.getId())
                .correlationId(event.getCorrelationId())
                .idempotencyKey(channelIdempotencyKey)
                .eventType(event.getEventType())
                .deliveryChannel(DeliveryChannel.valueOf(channel.toUpperCase()))
                .priority(template.getDefaultPriority())
                .title(template.getTitle())
                .message(finalMessage)
                .metadata(enrichedMetadata)
                .userReadStatus(UserReadStatus.UNREAD)
                .networkDeliveryStatus(NetworkDeliveryStatus.PENDING)
                .build();

        notificationRepository.save(notification);
        log.info("Saved PENDING notification for User: {} via Channel: {}", event.getUserId(), channel);

        deliveryManager.dispatch(notification);
    }

    /**
     * Replaces placeholders like {first_name} with actual values from the metadata
     * map.
     */
    private String hydrateTemplate(String templateBody, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty())
            return templateBody;

        String hydratedMessage = templateBody;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            hydratedMessage = hydratedMessage.replace(placeholder, String.valueOf(entry.getValue()));
        }
        return hydratedMessage;
    }

    private record ContactInfo(String firstName, String lastName, String email, String phone) {
    }
}