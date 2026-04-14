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
    // So the controller derives the current user from the JWT instead of asking the
    // client to send userId in the URL.

    private final UnreadCounterCache unreadCounterCache;
    private final NotificationStateService notificationStateService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(Authentication authentication) {
        // We now derive the current user directly from the JWT instead of asking the
        // client to send userId in the URL.
        UUID authenticatedUserId = authenticatedUserService.getAuthenticatedUserId(authentication);
        return ResponseEntity.ok(unreadCounterCache.getUnreadCounter(authenticatedUserId));
    }

    @PostMapping("/{notificationId}/mark-read")
    public ResponseEntity<Void> markAsRead(
            Authentication authentication,
            @PathVariable UUID notificationId) {

        UUID authenticatedUserId = authenticatedUserService.getAuthenticatedUserId(authentication);
        notificationStateService.markNotificationAsRead(notificationId, authenticatedUserId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/mark-read")
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {

        UUID authenticatedUserId = authenticatedUserService.getAuthenticatedUserId(authentication);
        notificationStateService.markAllNotificationsAsRead(authenticatedUserId);
        return ResponseEntity.ok().build();
    }

}
