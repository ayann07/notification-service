package notification_service.controller;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import notification_service.dto.DeviceRegistrationRequest;
import notification_service.security.AuthenticatedUserService;
import notification_service.service.DeviceTokenService;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceTokenController {
    // Device token APIs are sensitive because they control where push notifications
    // will be sent. So we enforce ownership before register/unregister operations.

    private final DeviceTokenService deviceTokenService;
    private final AuthenticatedUserService authenticatedUserService;

    @PostMapping("/register")
    public ResponseEntity<String> registerDevice(
            Authentication authentication,
            @Valid @RequestBody DeviceRegistrationRequest deviceRegistrationRequest) {
        // The authenticated user owns the device token registration automatically, so
        // we no longer need userId in the request body.
        UUID authenticatedUserId = authenticatedUserService.getAuthenticatedUserId(authentication);
        deviceTokenService.registerDeviceToken(authenticatedUserId,
                deviceRegistrationRequest.getToken(), deviceRegistrationRequest.getDeviceType());
        return ResponseEntity.ok("Device token registered successfully");
    }

    @DeleteMapping("/unregister")
    public ResponseEntity<String> unregisterDevice(Authentication authentication, @RequestParam String token) {
        // Here we derive the user directly from the JWT and delete only if the token
        // belongs to that user.
        deviceTokenService.unregisterDeviceToken(authenticatedUserService.getAuthenticatedUserId(authentication), token);
        return ResponseEntity.ok("Device token deleted successfully");
    }

}
