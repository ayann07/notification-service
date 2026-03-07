package notification_service.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import notification_service.service.NotificationStateService;
import notification_service.service.UnreadCounterService;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final UnreadCounterService unreadCounterService;
    private final NotificationStateService notificationStateService;

    @GetMapping("/unread-count/{userId}")
    public ResponseEntity<Long> getUnreadCount(@PathVariable UUID userId) {
        return ResponseEntity.ok(unreadCounterService.getUnreadCounter(userId));
    }

    @PostMapping("/{notificationId}/mark-read/{userId}")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID notificationId,
            @PathVariable UUID userId) {

        notificationStateService.markNotificationAsRead(notificationId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/mark-read/{userId}")
    public ResponseEntity<Void> markAllAsRead(
            @PathVariable UUID userId) {

        notificationStateService.markAllNotificationsAsRead(userId);
        return ResponseEntity.ok().build();
    }

}
