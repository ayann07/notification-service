package notification_service.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import notification_service.dto.ChannelPreferenceToggleRequest;
import notification_service.dto.EventPreferenceToggleRequest;
import notification_service.model.NotificationPreference;
import notification_service.service.NotificationPreferenceService;

@RestController
@RequestMapping("/notification/preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    @GetMapping("/{userId}")
    public ResponseEntity<NotificationPreference> getPreferences(@PathVariable UUID userId) {
        return ResponseEntity.ok(preferenceService.getNotificationPreferences(userId));
    }

    @PatchMapping("/channel")
    public ResponseEntity<NotificationPreference> toggleChannel(@RequestBody ChannelPreferenceToggleRequest request) {
        NotificationPreference updatedPref = preferenceService.toggleChannel(request.getUserId(),
                request.getDeliveryChannel(), request.isMute());
        return ResponseEntity.ok(updatedPref);
    }

    @PatchMapping("/event")
    public ResponseEntity<NotificationPreference> toggleEvent(@RequestBody EventPreferenceToggleRequest request) {
        NotificationPreference updatedPref = preferenceService.toggleEvent(request.getUserId(),
                request.getEventType(), request.isMute());
        return ResponseEntity.ok(updatedPref);
    }
}
