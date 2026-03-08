package notification_service.service;

import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification_service.cache.IdempotencyCache;
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
    private final IdempotencyCache idempotencyCache;

    @Transactional
    public void process(NotificationEvent event) {
        log.info("Processing event with correlationId:{}", event.getCorrelationId());
        if (idempotencyCache.isDuplicate(event.getIdempotencyKey())) {
            return;
        }
        ContactInfo contactInfo = resolveContactInfo(event);
        List<NotificationTemplate> templates = getActiveTemplatesByEventType(event.getEventType());
        if (templates.isEmpty()) {
            return;
        }
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

        for (NotificationTemplate template : templates) {
            String finalMessage = hydrateTemplate(template.getBody(), enrichedMetadata);
            String finalTitle = template.getTitle() != null
                    ? hydrateTemplate(template.getTitle(), enrichedMetadata)
                    : null;
            DeliveryChannel channel = template.getDeliveryChannel();

            processSingleChannel(channel, event, contactInfo, template, preference, finalMessage, finalTitle,
                    enrichedMetadata);
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

    private List<NotificationTemplate> getActiveTemplatesByEventType(String eventType) {
        List<NotificationTemplate> templates = templateRepository.findAllByEventTypeAndIsActiveTrue(eventType);
        if (templates.isEmpty()) {
            log.warn("No active templates found with given eventType: {}", eventType);
        }
        return templates;
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

    private void processSingleChannel(DeliveryChannel channel, NotificationEvent event, ContactInfo contactInfo,
            NotificationTemplate template, NotificationPreference preference,
            String finalMessage, String finalTitle, Map<String, Object> enrichedMetadata) {

        if (channel == DeliveryChannel.PUSH_NOTIFICATION && event.getRecipientType() == RecipientType.GUEST) {
            log.warn("⚠️ Skipping PUSH_NOTIFICATION for GUEST user. Guests do not have device tokens.");
            return;
        }

        if (preference.getMutedChannels() != null && preference.getMutedChannels().contains(channel.name())) {
            log.info("User {} muted channel {}. Skipping this channel.", event.getUserId(), channel);
            return;
        }

        Notification notification = Notification.builder()
                .userId(event.getUserId())
                .recipientEmail(contactInfo.email())
                .recipientPhone(contactInfo.phone())
                .templateId(template.getId())
                .correlationId(event.getCorrelationId())
                .idempotencyKey(event.getIdempotencyKey())
                .eventType(event.getEventType())
                .deliveryChannel(channel)
                .priority(template.getDefaultPriority())
                .title(finalTitle)
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