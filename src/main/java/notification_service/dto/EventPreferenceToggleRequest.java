package notification_service.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class EventPreferenceToggleRequest {

    @NotBlank(message = "userId is required")
    private UUID userId;

    @NotBlank(message = "eventType cannot be blank")
    private String eventType;

    @NotEmpty(message = "mute cannot be empty")
    private boolean mute;

}
