package notification_service.service;

import java.util.*;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification_service.dto.NotificationEvent;
import notification_service.model.Notification;
import notification_service.model.NotificationPreference;
import notification_service.model.NotificationTemplate;
import notification_service.repository.NotificationPreferenceRepository;
import notification_service.repository.NotificationRepository;
import notification_service.repository.NotificationTemplateRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationProcessingService {

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final DeliveryManagerService deliveryManager;

    public void process(NotificationEvent event) {
        log.info("processing event : {}", event.getCorrelationId());
        if (event.getIdempotencyKey() != null
                && notificationRepository.existsByIdempotencyKey(event.getIdempotencyKey())) {
            log.info("duplicate event detected, skipping idempotency key: {}", event.getIdempotencyKey());
            return;
        }

        NotificationTemplate template = templateRepository.findByEventTypeAndIsActiveTrue(event.getEventType())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Template not found or inactive for event: " + event.getEventType()));

        NotificationPreference preference = preferenceRepository.findById(event.getUserId())
                .orElse(new NotificationPreference());

        if (preference.getMutedEvents() != null && preference.getMutedChannels().contains(event.getEventType())) {
            log.info("User {} muted event {}. Dropping notification.", event.getUserId(), event.getEventType());
            return;
        }

        String finalMessage = hydrateTemplate(template.getBody(), event.getMetadata());
        List<String> targetChannels = template.getDefaultChannels();
        for (String channel : targetChannels) {
            if (preference.getMutedChannels() != null && preference.getMutedChannels().contains(channel)) {
                log.info("User {} muted channel {}. Skipping this channel.", event.getUserId(), channel);
                continue;
            }

            Notification notification = Notification.builder()
                    .userId(event.getUserId())
                    .templateId(template.getId())
                    .correlationId(event.getCorrelationId())
                    // We append the channel to the idempotency key so EMAIL and SMS don't block
                    // each other
                    .idempotencyKey(
                            event.getIdempotencyKey() != null ? event.getIdempotencyKey() + "-" + channel : null)
                    .eventType(event.getEventType())
                    .deliveryChannel(channel)
                    .priority(template.getDefaultPriority())
                    .title(template.getTitle()) // For a real app, you'd hydrate the title too!
                    .message(finalMessage)
                    .metadata(event.getMetadata())
                    .userReadStatus("UNREAD")
                    .networkDeliveryStatus("PENDING")
                    .build();

            notificationRepository.save(notification);
            log.info("Saved PENDING notification for User: {} via Channel: {}", event.getUserId(), channel);

            deliveryManager.dispatch(notification);
        }
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
}