package notification_service.dto;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import notification_service.enums.RecipientType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    @NotNull(message = "recipientType is required (REGISTERED_USER or GUEST)")
    private RecipientType recipientType;
    private UUID userId;

    @Valid
    // @Valid tells Spring to go inside this object and check its annotations!
    private GuestUserDetails guestUserDetails;

    @NotNull(message = "eventType is missing")
    private String eventType;
    private String correlationId;
    private String idempotencyKey;
    // The dynamic data for the template (e.g., {"amount": 500, "order_id": "123"})
    private Map<String, Object> metadata;
}
