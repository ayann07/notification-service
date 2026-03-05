package notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateResponseDTO {
    private String eventType;
    private String title;
    private String body;
    private List<String> defaultChannels;
    private Integer defaultPriority;
}