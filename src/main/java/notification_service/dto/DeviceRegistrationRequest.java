package notification_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import notification_service.enums.DeviceType;

@Data
public class DeviceRegistrationRequest {

    // The frontend/device should only send the token and device type.
    // Which user owns the token is now derived from the JWT.
    @NotBlank(message = "token is required")
    private String token;

    @NotNull(message = "deviceType is required")
    private DeviceType deviceType;
}
