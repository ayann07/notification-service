package notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DltReplayRequest {

    private String topic;
    private Integer partition;
    private Long offset;

    @Builder.Default
    private boolean dryRun = false;

    @Builder.Default
    private boolean regenerateIdempotencyKey = true;

    @Builder.Default
    private boolean regenerateCorrelationId = false;
}
