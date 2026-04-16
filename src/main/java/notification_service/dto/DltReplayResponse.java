package notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DltReplayResponse {

    private String sourceTopic;
    private String targetTopic;
    private Integer partition;
    private Long offset;
    private boolean dryRun;
    private Long replayAttempt;
    private Long maxReplayAttempts;
    private String actor;
    private String originalIdempotencyKey;
    private String replayIdempotencyKey;
    private String originalCorrelationId;
    private String replayCorrelationId;
    private NotificationEvent replayEventPreview;
    private String message;
}
