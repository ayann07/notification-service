package notification_service.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import notification_service.enums.DeviceType;

@Data
public class DeviceRegistrationRequest {

    @NotBlank(message = "userId is required")
    private UUID userId;

    @NotBlank(message = "token is required")
    private String token;

    @NotNull(message = "recipientType is required (REGISTERED_USER or GUEST)")
    private DeviceType deviceType;
}
