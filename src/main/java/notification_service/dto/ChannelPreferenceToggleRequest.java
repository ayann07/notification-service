package notification_service.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import notification_service.enums.DeliveryChannel;

@Data
public class ChannelPreferenceToggleRequest {
    @NotBlank(message = "userId cannot be empty")
    private UUID userId;

    @NotNull(message = "DeliveryChannel cannot be null")
    private DeliveryChannel deliveryChannel;

    @NotEmpty(message = "mute cannot be empty")
    private boolean mute;

}
