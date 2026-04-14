package notification_service.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import notification_service.cache.UnreadCounterCache;
import notification_service.security.AuthenticatedUserService;
import notification_service.service.NotificationStateService;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    // These endpoints are user-owned actions.
    // Even though the API currently still accepts userId in the path, we no longer
    // trust that value blindly. We compare it with the JWT subject first.

    private final UnreadCounterCache unreadCounterCache;
    private final NotificationStateService notificationStateService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping("/unread-count/{userId}")
    public ResponseEntity<Long> getUnreadCount(Authentication authentication, @PathVariable UUID userId) {
        // Ownership check: the logged-in user can fetch only their own unread count.
        authenticatedUserService.ensureUserOwnsResource(authentication, userId);
        return ResponseEntity.ok(unreadCounterCache.getUnreadCounter(userId));
    }

    @PostMapping("/{notificationId}/mark-read/{userId}")
    public ResponseEntity<Void> markAsRead(
            Authentication authentication,
            @PathVariable UUID notificationId,
            @PathVariable UUID userId) {

        // Ownership check before mutating notification state.
        authenticatedUserService.ensureUserOwnsResource(authentication, userId);
        notificationStateService.markNotificationAsRead(notificationId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/mark-read/{userId}")
    public ResponseEntity<Void> markAllAsRead(
            Authentication authentication,
            @PathVariable UUID userId) {

        // Same ownership rule for "mark all as read".
        authenticatedUserService.ensureUserOwnsResource(authentication, userId);
        notificationStateService.markAllNotificationsAsRead(userId);
        return ResponseEntity.ok().build();
    }

}
