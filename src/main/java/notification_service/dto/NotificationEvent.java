package notification_service.dto;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "producerName is mandatory (e.g., 'SAP_BILLING_SERVICE', 'WALLET_SERVICE')")
    private String producerName;

    @NotNull(message = "recipientType is required (REGISTERED_USER or GUEST)")
    private RecipientType recipientType;
    private UUID userId;

    @Valid
    // @Valid tells Spring to go inside this object and check its annotations!
    private GuestUserDetails guestUserDetails;

    @NotBlank(message = "eventType key is mandatory and cannot be blank")
    private String eventType;
    private String correlationId;

    @NotBlank(message = "Idempotency key is mandatory and cannot be blank")
    private String idempotencyKey;
    // The dynamic data for the template (e.g., {"amount": 500, "order_id": "123"})
    private Map<String, Object> metadata;
}
