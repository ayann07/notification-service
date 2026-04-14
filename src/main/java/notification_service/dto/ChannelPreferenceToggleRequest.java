package notification_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import notification_service.enums.DeliveryChannel;

@Data
public class ChannelPreferenceToggleRequest {
    // The authenticated user now comes from the JWT, so the frontend no longer
    // needs to send userId for this user-owned action.
    @NotNull(message = "DeliveryChannel cannot be null")
    private DeliveryChannel deliveryChannel;

    private boolean mute;

}
