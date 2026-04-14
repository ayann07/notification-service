package notification_service.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    // Preference APIs now derive the user from the JWT.
    // This is cleaner and safer than asking the frontend to send userId for "my
    // preferences" operations.

    private final NotificationPreferenceService preferenceService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping("/me")
    public ResponseEntity<NotificationPreference> getPreferences(Authentication authentication) {
        UUID authenticatedUserId = authenticatedUserService.getAuthenticatedUserId(authentication);
        return ResponseEntity.ok(preferenceService.getNotificationPreferences(authenticatedUserId));
    }

    @PatchMapping("/channel")
    public ResponseEntity<NotificationPreference> toggleChannel(
            Authentication authentication,
            @Valid @RequestBody ChannelPreferenceToggleRequest request) {
        UUID authenticatedUserId = authenticatedUserService.getAuthenticatedUserId(authentication);
        NotificationPreference updatedPref = preferenceService.toggleChannel(authenticatedUserId,
                request.getDeliveryChannel(), request.isMute());
        return ResponseEntity.ok(updatedPref);
    }

    @PatchMapping("/event")
    public ResponseEntity<NotificationPreference> toggleEvent(
            Authentication authentication,
            @Valid @RequestBody EventPreferenceToggleRequest request) {
        UUID authenticatedUserId = authenticatedUserService.getAuthenticatedUserId(authentication);
        NotificationPreference updatedPref = preferenceService.toggleEvent(authenticatedUserId,
                request.getEventType(), request.isMute());
        return ResponseEntity.ok(updatedPref);
    }
}
