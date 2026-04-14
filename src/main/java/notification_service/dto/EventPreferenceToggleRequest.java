package notification_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EventPreferenceToggleRequest {

    // The event name still needs to come from the client because it describes what
    // preference is being changed.
    @NotBlank(message = "eventType cannot be blank")
    private String eventType;

    private boolean mute;

}
