package notification_service.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import notification_service.delivery.ChannelSender;
import notification_service.enums.DeliveryChannel;
import notification_service.enums.NetworkDeliveryStatus;
import notification_service.model.Notification;
import notification_service.repository.NotificationRepository;

@Slf4j
@Service
public class DeliveryManagerService {
    private final NotificationRepository notificationRepository;
    private final Map<DeliveryChannel, ChannelSender> senderRegistry;

    public DeliveryManagerService(List<ChannelSender> senders, NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;

        // We convert the list into a Map so we can instantly look up the right sender
        // by its name (e.g., "EMAIL" -> EmailSender)
        this.senderRegistry = senders.stream()
                .collect(Collectors.toMap(ChannelSender::getChannelType, sender -> sender));
    }

    public void dispatch(Notification notification) {
        ChannelSender sender = senderRegistry.get(notification.getDeliveryChannel());
        if (sender == null) {
            log.error("No sender found for this channel : {}", notification.getDeliveryChannel());
            updateStatus(notification, NetworkDeliveryStatus.FAILED);
            return;
        }
        try {
            // Trigger the actual Email or SMS
            sender.send(notification);
            updateStatus(notification, NetworkDeliveryStatus.SENT);

        } catch (Exception e) {
            log.error("Failed to deliver notification {}. Error: {}", notification.getId(), e.getMessage());
            updateStatus(notification, NetworkDeliveryStatus.FAILED);
        }
    }

    private void updateStatus(Notification notification, NetworkDeliveryStatus status) {
        notification.setNetworkDeliveryStatus(status);
        notificationRepository.save(notification);
    }
}
