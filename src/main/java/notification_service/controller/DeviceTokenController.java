package notification_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import notification_service.dto.DeviceRegistrationRequest;
import notification_service.service.DeviceTokenService;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    public ResponseEntity<String> registerDevice(@RequestBody DeviceRegistrationRequest deviceRegistrationRequest) {
        deviceTokenService.registerDeviceToken(deviceRegistrationRequest.getUserId(),
                deviceRegistrationRequest.getToken(), deviceRegistrationRequest.getDeviceType());
        return ResponseEntity.ok("Device token registered successfully");
    }

}
