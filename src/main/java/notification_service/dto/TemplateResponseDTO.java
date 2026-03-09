package notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import notification_service.enums.DeliveryChannel;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateResponseDTO {
    private String eventType;
    private String title;
    private String body;
    private DeliveryChannel deliveryChannel;
    private Integer defaultPriority;
}