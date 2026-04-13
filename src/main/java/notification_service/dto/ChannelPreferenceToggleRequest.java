package notification_service.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import notification_service.enums.DeliveryChannel;

@Data
public class ChannelPreferenceToggleRequest {
    @NotNull(message = "userId cannot be null")
    private UUID userId;

    @NotNull(message = "DeliveryChannel cannot be null")
    private DeliveryChannel deliveryChannel;

    private boolean mute;

}
