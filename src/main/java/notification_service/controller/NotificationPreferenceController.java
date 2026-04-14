package notification_service.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import notification_service.dto.ChannelPreferenceToggleRequest;
import notification_service.dto.EventPreferenceToggleRequest;
import notification_service.model.NotificationPreference;
import notification_service.security.AuthenticatedUserService;
import notification_service.service.NotificationPreferenceService;

@RestController
@RequestMapping("/notification/preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {
    // Preference APIs are also user-owned actions, so we verify the JWT subject
    // against the requested userId.

    private final NotificationPreferenceService preferenceService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping("/{userId}")
    public ResponseEntity<NotificationPreference> getPreferences(Authentication authentication, @PathVariable UUID userId) {
        // A user can view only their own preferences.
        authenticatedUserService.ensureUserOwnsResource(authentication, userId);
        return ResponseEntity.ok(preferenceService.getNotificationPreferences(userId));
    }

    @PatchMapping("/channel")
    public ResponseEntity<NotificationPreference> toggleChannel(
            Authentication authentication,
            @Valid @RequestBody ChannelPreferenceToggleRequest request) {
        // The userId inside the request body must belong to the authenticated user.
        authenticatedUserService.ensureUserOwnsResource(authentication, request.getUserId());
        NotificationPreference updatedPref = preferenceService.toggleChannel(request.getUserId(),
                request.getDeliveryChannel(), request.isMute());
        return ResponseEntity.ok(updatedPref);
    }

    @PatchMapping("/event")
    public ResponseEntity<NotificationPreference> toggleEvent(
            Authentication authentication,
            @Valid @RequestBody EventPreferenceToggleRequest request) {
        // Same ownership protection for event-level mute/unmute changes.
        authenticatedUserService.ensureUserOwnsResource(authentication, request.getUserId());
        NotificationPreference updatedPref = preferenceService.toggleEvent(request.getUserId(),
                request.getEventType(), request.isMute());
        return ResponseEntity.ok(updatedPref);
    }
}
