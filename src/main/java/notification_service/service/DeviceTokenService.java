package notification_service.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification_service.enums.DeviceType;
import notification_service.repository.DeviceTokenRepository;
import notification_service.model.DeviceToken;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    @Transactional
    public void registerDeviceToken(UUID userId, String token, DeviceType deviceType) {
        Optional<DeviceToken> deviceToken = deviceTokenRepository.findByToken(token);
        if (deviceToken.isEmpty()) {
            DeviceToken newDeviceToken = DeviceToken.builder().userId(userId).deviceType(deviceType).token(token)
                    .build();
            deviceTokenRepository.save(newDeviceToken);
            log.info("Registered new device");
        } else {
            DeviceToken existingToken = deviceToken.get();
            existingToken.setUpdatedAt(LocalDateTime.now());
            deviceTokenRepository.save(existingToken);
            log.info("Updated existing device token.");
        }
    }

    @Transactional
    public void unregisterDeviceToken(String token) {
        deviceTokenRepository.deleteByToken(token);
    }

}
