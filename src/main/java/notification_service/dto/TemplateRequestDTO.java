package notification_service.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TemplateRequestDTO {

    @NotBlank(message = "Event type cannot be blank")
    private String eventType;

    @NotBlank(message = "Title cannot be blank")
    private String title;

    @NotBlank(message = "Body template cannot be blank")
    @Size(min = 10, message = "Body template is too short")
    private String body;

    @NotNull(message = "Channels list cannot be null")
    private List<String> defaultChannels;

    private Integer defaultPriority;
}