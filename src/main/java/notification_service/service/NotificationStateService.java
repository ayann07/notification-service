package notification_service.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification_service.cache.UnreadCounterCache;
import notification_service.repository.NotificationRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationStateService {

    private final NotificationRepository notificationRepository;
    private final UnreadCounterCache unreadCounterCache;

    @Transactional
    public void markNotificationAsRead(UUID notificationId, UUID userId) {
        int updatedRows = notificationRepository.markAsRead(notificationId, userId);
        if (updatedRows > 0) {
            log.info("row got updated in postgres, so updating the counter in redis");
            unreadCounterCache.decrement(userId);
        } else {
            log.debug("Notification {} already read. Ignoring.", notificationId);
        }
    }

    @Transactional
    public void markAllNotificationsAsRead(UUID userId) {
        int updatedRows = notificationRepository.markAllAsRead(userId);
        if (updatedRows > 0) {
            log.info("DB updated {} rows. Resetting Redis counter to 0 for user: {}", updatedRows, userId);
            unreadCounterCache.reset(userId);
        } else {
            log.debug("No unread notifications found for user {}. Redis remains untouched.", userId);
        }
    }

}
