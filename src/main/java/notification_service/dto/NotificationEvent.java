package notification_service.dto;

import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private UUID userId;
    private String eventType;
    private String correlationId;
    private String idempotencyKey;
    // The dynamic data for the template (e.g., {"amount": 500, "order_id": "123"})
    private Map<String, Object> metadata;
}
