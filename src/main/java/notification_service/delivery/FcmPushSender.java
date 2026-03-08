package notification_service.delivery;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification_service.enums.DeliveryChannel;
import notification_service.model.DeviceToken;
import notification_service.model.Notification;
import notification_service.repository.DeviceTokenRepository;
import java.util.*;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;

@Slf4j
@Component
@RequiredArgsConstructor
public class FcmPushSender implements ChannelSender {

    private final DeviceTokenRepository deviceTokenRepository;

    @Override
    public DeliveryChannel getChannelType() {
        return DeliveryChannel.PUSH_NOTIFICATION;
    }

    @Override
    public void send(Notification notification) {
        log.info("preparing OS level push notification for user: {}", notification.getUserId());
        List<DeviceToken> userDevices = deviceTokenRepository.findAllByUserId(notification.getUserId());
        if (userDevices.isEmpty()) {
            log.warn("No active devices found for user: {}. Skipping push notification.", notification.getUserId());
            return;
        }

        com.google.firebase.messaging.Notification fcmBanner = com.google.firebase.messaging.Notification.builder()
                .setTitle(notification.getTitle()).setBody(notification.getMessage()).build();

        for (DeviceToken device : userDevices) {
            try {
                Message message = Message.builder().setNotification(fcmBanner).setToken(device.getToken()).build();
                String response = FirebaseMessaging.getInstance().send(message);
                log.info("Successfully sent PUSH to {} device. Firebase ID: {}", device.getDeviceType(), response);

            } catch (Exception e) {
                log.error("Failed to send push notification to token {} :{}", device.getToken(), e.getMessage());
            }
        }
    }

}
