package notification_service.service;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification_service.dto.DltReplayRequest;
import notification_service.dto.DltReplayResponse;
import notification_service.dto.NotificationEvent;
import notification_service.exceptions.InvalidRequestException;
import notification_service.exceptions.ResourceConflictException;
import notification_service.exceptions.ResourceNotFoundException;
import notification_service.messaging.NotificationProducer;

@Slf4j
@Service
@RequiredArgsConstructor
public class DltReplayService {

    private static final String REPLAY_ATTEMPT_PREFIX = "dlt-replay-attempt:";

    private final NotificationProducer notificationProducer;
    private final ConsumerFactory<String, NotificationEvent> consumerFactory;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.kafka.topics.notification-events:notification-events}")
    private String notificationEventsTopic;

    @Value("${app.kafka.dlt.suffix:.dlt}")
    private String dltSuffix;

    @Value("${app.kafka.dlt.replay.fetch-timeout-ms:3000}")
    private long fetchTimeoutMs;

    @Value("${app.kafka.dlt.replay.max-attempts-per-record:3}")
    private long maxReplayAttemptsPerRecord;

    @Value("${app.kafka.dlt.replay.attempt-ttl-hours:168}")
    private long replayAttemptTtlHours;

    public DltReplayResponse replay(DltReplayRequest request, Authentication authentication) {
        validateRequest(request);

        ConsumerRecord<String, NotificationEvent> record = fetchRecord(
                request.getTopic(),
                request.getPartition(),
                request.getOffset());

        NotificationEvent originalEvent = record.value();
        if (originalEvent == null) {
            throw new ResourceNotFoundException("The dead-letter record exists but does not contain a NotificationEvent payload.");
        }

        NotificationEvent replayEvent = cloneEvent(originalEvent);
        if (request.isRegenerateIdempotencyKey()) {
            replayEvent.setIdempotencyKey(regeneratedValue(originalEvent.getIdempotencyKey(), "replay-idem"));
        }
        if (request.isRegenerateCorrelationId()) {
            replayEvent.setCorrelationId(regeneratedValue(originalEvent.getCorrelationId(), "replay-corr"));
        }

        String actor = authentication != null ? authentication.getName() : "unknown";
        String authorities = stringifyAuthorities(authentication);
        long attemptsSoFar = currentReplayAttempts(request.getTopic(), request.getPartition(), request.getOffset());

        if (!request.isDryRun() && attemptsSoFar >= maxReplayAttemptsPerRecord) {
            throw new ResourceConflictException(
                    "Replay attempt limit reached for this dead-letter record. topic=%s partition=%d offset=%d limit=%d"
                            .formatted(request.getTopic(), request.getPartition(), request.getOffset(),
                                    maxReplayAttemptsPerRecord));
        }

        Long replayAttempt = request.isDryRun()
                ? attemptsSoFar
                : incrementReplayAttempts(request.getTopic(), request.getPartition(), request.getOffset());

        auditReplay(request, originalEvent, replayEvent, actor, authorities, replayAttempt);

        if (!request.isDryRun()) {
            notificationProducer.publishEvent(replayEvent);
        }

        return DltReplayResponse.builder()
                .sourceTopic(request.getTopic())
                .targetTopic(notificationEventsTopic)
                .partition(request.getPartition())
                .offset(request.getOffset())
                .dryRun(request.isDryRun())
                .replayAttempt(replayAttempt)
                .maxReplayAttempts(maxReplayAttemptsPerRecord)
                .actor(actor)
                .originalIdempotencyKey(originalEvent.getIdempotencyKey())
                .replayIdempotencyKey(replayEvent.getIdempotencyKey())
                .originalCorrelationId(originalEvent.getCorrelationId())
                .replayCorrelationId(replayEvent.getCorrelationId())
                .replayEventPreview(replayEvent)
                .message(request.isDryRun()
                        ? "Dry run completed. No replay was published."
                        : "Replay event published back to the main Kafka topic.")
                .build();
    }

    private void validateRequest(DltReplayRequest request) {
        if (request == null) {
            throw new InvalidRequestException("A replay request body is required.");
        }
        if (request.getTopic() == null || request.getTopic().isBlank()) {
            throw new InvalidRequestException("topic is required.");
        }
        if (!request.getTopic().endsWith(dltSuffix)) {
            throw new InvalidRequestException("Only dead-letter topics ending with '%s' can be replayed.".formatted(dltSuffix));
        }
        if (request.getPartition() == null || request.getPartition() < 0) {
            throw new InvalidRequestException("partition must be zero or greater.");
        }
        if (request.getOffset() == null || request.getOffset() < 0) {
            throw new InvalidRequestException("offset must be zero or greater.");
        }
    }

    private ConsumerRecord<String, NotificationEvent> fetchRecord(String topic, int partition, long offset) {
        TopicPartition topicPartition = new TopicPartition(topic, partition);

        try (Consumer<String, NotificationEvent> consumer = consumerFactory.createConsumer(
                "notification-recovery",
                "notification-recovery-client-" + UUID.randomUUID(),
                null)) {

            consumer.assign(List.of(topicPartition));
            consumer.seek(topicPartition, offset);

            long deadline = System.currentTimeMillis() + fetchTimeoutMs;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, NotificationEvent> records = consumer.poll(Duration.ofMillis(250));
                for (ConsumerRecord<String, NotificationEvent> record : records.records(topicPartition)) {
                    if (record.offset() == offset) {
                        return record;
                    }
                }
            }
        }

        throw new ResourceNotFoundException(
                "No dead-letter record found at topic=%s partition=%d offset=%d".formatted(topic, partition, offset));
    }

    private long currentReplayAttempts(String topic, int partition, long offset) {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        String value = ops.get(replayAttemptKey(topic, partition, offset));
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private long incrementReplayAttempts(String topic, int partition, long offset) {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        String key = replayAttemptKey(topic, partition, offset);
        Long updated = ops.increment(key);
        if (updated != null && updated == 1L) {
            redisTemplate.expire(key, Duration.ofHours(replayAttemptTtlHours));
        }
        return updated == null ? 0L : updated;
    }

    private String replayAttemptKey(String topic, int partition, long offset) {
        return REPLAY_ATTEMPT_PREFIX + topic + ":" + partition + ":" + offset;
    }

    private NotificationEvent cloneEvent(NotificationEvent event) {
        return NotificationEvent.builder()
                .producerName(event.getProducerName())
                .recipientType(event.getRecipientType())
                .userId(event.getUserId())
                .guestUserDetails(event.getGuestUserDetails())
                .eventType(event.getEventType())
                .correlationId(event.getCorrelationId())
                .idempotencyKey(event.getIdempotencyKey())
                .metadata(event.getMetadata())
                .build();
    }

    private String regeneratedValue(String originalValue, String prefix) {
        if (originalValue == null || originalValue.isBlank()) {
            return prefix + "-" + UUID.randomUUID();
        }
        return originalValue + "-replay-" + UUID.randomUUID();
    }

    private void auditReplay(DltReplayRequest request, NotificationEvent originalEvent, NotificationEvent replayEvent,
            String actor, String authorities, Long replayAttempt) {
        log.warn(
                "DLT replay requested. actor={} authorities={} sourceTopic={} partition={} offset={} dryRun={} replayAttempt={} maxReplayAttempts={} originalIdempotencyKey={} replayIdempotencyKey={} originalCorrelationId={} replayCorrelationId={} eventType={}",
                actor,
                authorities,
                request.getTopic(),
                request.getPartition(),
                request.getOffset(),
                request.isDryRun(),
                replayAttempt,
                maxReplayAttemptsPerRecord,
                originalEvent.getIdempotencyKey(),
                replayEvent.getIdempotencyKey(),
                originalEvent.getCorrelationId(),
                replayEvent.getCorrelationId(),
                originalEvent.getEventType());
    }

    private String stringifyAuthorities(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return "[]";
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return authorities.stream().map(GrantedAuthority::getAuthority).toList().toString();
    }
}
