package notification_service.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import notification_service.cache.UnreadCounterCache;
import notification_service.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationStateServiceTest {
    // This class tests the coordination between database read-state updates and the
    // Redis unread counter cache.

    @Mock
    // Fake repository that reports how many rows were updated.
    private NotificationRepository notificationRepository;

    @Mock
    // Fake unread cache used to verify decrement/reset behavior.
    private UnreadCounterCache unreadCounterCache;

    @InjectMocks
    // Real service under test.
    private NotificationStateService notificationStateService;

    @Test
    void markNotificationAsReadDecrementsUnreadCounterWhenRowWasUpdated() {
        UUID notificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(notificationRepository.markAsRead(notificationId, userId)).thenReturn(1);

        // If one DB row changed from UNREAD to READ, Redis should be decremented too.
        notificationStateService.markNotificationAsRead(notificationId, userId);

        verify(unreadCounterCache).decrement(userId);
    }

    @Test
    void markNotificationAsReadLeavesCacheUntouchedWhenNothingChanged() {
        UUID notificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(notificationRepository.markAsRead(notificationId, userId)).thenReturn(0);

        // No changed DB rows means there is nothing to adjust in Redis.
        notificationStateService.markNotificationAsRead(notificationId, userId);

        verify(unreadCounterCache, never()).decrement(userId);
    }

    @Test
    void markAllNotificationsAsReadResetsCounterWhenRowsWereUpdated() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.markAllAsRead(userId)).thenReturn(3);

        // If multiple rows were marked READ, the unread counter should become 0.
        notificationStateService.markAllNotificationsAsRead(userId);

        verify(unreadCounterCache).reset(userId);
    }

    @Test
    void markAllNotificationsAsReadSkipsResetWhenNoRowsWereUpdated() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.markAllAsRead(userId)).thenReturn(0);

        // No unread rows in the DB means Redis should be left alone.
        notificationStateService.markAllNotificationsAsRead(userId);

        verify(unreadCounterCache, never()).reset(userId);
    }
}
