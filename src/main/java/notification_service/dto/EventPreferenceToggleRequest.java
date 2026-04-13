package notification_service.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EventPreferenceToggleRequest {

    @NotNull(message = "userId is required")
    private UUID userId;

    @NotBlank(message = "eventType cannot be blank")
    private String eventType;

    private boolean mute;

}
