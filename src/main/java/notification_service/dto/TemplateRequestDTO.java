package notification_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import notification_service.enums.DeliveryChannel;

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
    private DeliveryChannel deliveryChannel;

    private Integer defaultPriority;
}