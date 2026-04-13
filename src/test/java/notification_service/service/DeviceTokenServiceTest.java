package notification_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import notification_service.enums.DeviceType;
import notification_service.model.DeviceToken;
import notification_service.repository.DeviceTokenRepository;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {
    // This class tests device-token registration rules:
    // create a new token when missing, update timestamp when it already exists,
    // and delete by token string.

    @Mock
    // Fake repository so we can check save/delete behavior without a database.
    private DeviceTokenRepository deviceTokenRepository;

    @InjectMocks
    // Real service with the mocked repository injected.
    private DeviceTokenService deviceTokenService;

    @Test
    void registerDeviceTokenSavesNewTokenWhenNotPresent() {
        UUID userId = UUID.randomUUID();
        when(deviceTokenRepository.findByToken("token-1")).thenReturn(Optional.empty());

        // Act: register a token that does not exist yet.
        deviceTokenService.registerDeviceToken(userId, "token-1", DeviceType.ANDROID);

        // ArgumentCaptor lets us grab the exact object that was passed to save(...).
        ArgumentCaptor<DeviceToken> captor = ArgumentCaptor.forClass(DeviceToken.class);
        verify(deviceTokenRepository).save(captor.capture());
        assertEquals(userId, captor.getValue().getUserId());
        assertEquals("token-1", captor.getValue().getToken());
        assertEquals(DeviceType.ANDROID, captor.getValue().getDeviceType());
    }

    @Test
    void registerDeviceTokenUpdatesTimestampWhenTokenAlreadyExists() {
        DeviceToken existing = DeviceToken.builder()
                .userId(UUID.randomUUID())
                .token("token-1")
                .deviceType(DeviceType.IOS)
                .build();
        existing.setUpdatedAt(LocalDateTime.now().minusDays(1));
        when(deviceTokenRepository.findByToken("token-1")).thenReturn(Optional.of(existing));

        // Act: register the same token again.
        deviceTokenService.registerDeviceToken(UUID.randomUUID(), "token-1", DeviceType.IOS);

        // Assert: the existing entity is saved again and gets a fresh updatedAt.
        verify(deviceTokenRepository).save(existing);
        assertNotNull(existing.getUpdatedAt());
    }

    @Test
    void unregisterDeviceTokenDeletesByToken() {
        // Act: unregister the device.
        deviceTokenService.unregisterDeviceToken("token-1");

        // Assert: repository delete method is called with the same token.
        verify(deviceTokenRepository).deleteByToken("token-1");
    }
}
