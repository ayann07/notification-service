package notification_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import notification_service.dto.DeviceRegistrationRequest;
import notification_service.service.DeviceTokenService;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @PostMapping("/register")
    public ResponseEntity<String> registerDevice(@RequestBody DeviceRegistrationRequest deviceRegistrationRequest) {
        deviceTokenService.registerDeviceToken(deviceRegistrationRequest.getUserId(),
                deviceRegistrationRequest.getToken(), deviceRegistrationRequest.getDeviceType());
        return ResponseEntity.ok("Device token registered successfully");
    }

    @DeleteMapping("/unregister")
    public ResponseEntity<String> unregisterDevice(@RequestParam String token) {
        deviceTokenService.unregisterDeviceToken(token);
        return ResponseEntity.ok("Device token deleted successfully");
    }

}
