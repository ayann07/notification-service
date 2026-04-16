package notification_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import notification_service.dto.DltReplayRequest;
import notification_service.dto.DltReplayResponse;
import notification_service.dto.NotificationEvent;
import notification_service.enums.RecipientType;
import notification_service.exceptions.ResourceConflictException;
import notification_service.messaging.NotificationProducer;

@ExtendWith(MockitoExtension.class)
class DltReplayServiceTest {

    @Mock
    private NotificationProducer notificationProducer;

    @Mock
    private ConsumerFactory<String, NotificationEvent> consumerFactory;

    @Mock
    private Consumer<String, NotificationEvent> consumer;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private DltReplayService dltReplayService;

    private final TopicPartition topicPartition = new TopicPartition("notification-events.dlt", 0);
    private final ConsumerRecord<String, NotificationEvent> record = new ConsumerRecord<>(
            "notification-events.dlt",
            0,
            15L,
            "idem-1",
            NotificationEvent.builder()
                    .producerName("ORDER_SERVICE")
                    .recipientType(RecipientType.REGISTERED_USER)
                    .eventType("ORDER_SHIPPED")
                    .correlationId("corr-1")
                    .idempotencyKey("idem-1")
                    .build());

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dltReplayService, "notificationEventsTopic", "notification-events");
        ReflectionTestUtils.setField(dltReplayService, "dltSuffix", ".dlt");
        ReflectionTestUtils.setField(dltReplayService, "fetchTimeoutMs", 1000L);
        ReflectionTestUtils.setField(dltReplayService, "maxReplayAttemptsPerRecord", 3L);
        ReflectionTestUtils.setField(dltReplayService, "replayAttemptTtlHours", 24L);

        when(consumerFactory.createConsumer(any(), any(), any())).thenReturn(consumer);
        when(consumer.poll(any(Duration.class))).thenReturn(new ConsumerRecords<>(Map.of(topicPartition, List.of(record))));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void replayPublishesEventAndTracksAttemptMetadata() {
        when(valueOperations.get("dlt-replay-attempt:notification-events.dlt:0:15")).thenReturn(null);
        when(valueOperations.increment("dlt-replay-attempt:notification-events.dlt:0:15")).thenReturn(1L);

        DltReplayResponse response = dltReplayService.replay(
                DltReplayRequest.builder()
                        .topic("notification-events.dlt")
                        .partition(0)
                        .offset(15L)
                        .build(),
                new TestingAuthenticationToken("ops-admin", "n/a", "ROLE_ADMIN"));

        verify(notificationProducer).publishEvent(argThat(replayed ->
                replayed != null
                        && replayed.getIdempotencyKey() != null
                        && replayed.getIdempotencyKey().startsWith("idem-1-replay-")
                        && "corr-1".equals(replayed.getCorrelationId())));
        verify(redisTemplate).expire("dlt-replay-attempt:notification-events.dlt:0:15", Duration.ofHours(24));
        assertEquals("ops-admin", response.getActor());
        assertEquals("notification-events.dlt", response.getSourceTopic());
        assertEquals("notification-events", response.getTargetTopic());
        assertEquals(1L, response.getReplayAttempt());
        assertFalse(response.isDryRun());
        assertEquals(15L, response.getOffset());
        assertNotEquals("idem-1", response.getReplayIdempotencyKey());
    }

    @Test
    void dryRunSkipsPublishingAndLeavesAttemptCounterUnchanged() {
        when(valueOperations.get("dlt-replay-attempt:notification-events.dlt:0:15")).thenReturn("2");

        DltReplayResponse response = dltReplayService.replay(
                DltReplayRequest.builder()
                        .topic("notification-events.dlt")
                        .partition(0)
                        .offset(15L)
                        .dryRun(true)
                        .regenerateCorrelationId(true)
                        .build(),
                new TestingAuthenticationToken(UUID.randomUUID().toString(), "n/a", "ROLE_INTERNAL"));

        verify(notificationProducer, never()).publishEvent(any());
        verify(valueOperations, never()).increment(any());
        assertTrueMessage(response.getMessage());
        assertEquals(2L, response.getReplayAttempt());
        assertNotEquals("corr-1", response.getReplayCorrelationId());
    }

    @Test
    void replayRejectsRequestsOverConfiguredAttemptLimit() {
        when(valueOperations.get("dlt-replay-attempt:notification-events.dlt:0:15")).thenReturn("3");

        assertThrows(ResourceConflictException.class, () -> dltReplayService.replay(
                DltReplayRequest.builder()
                        .topic("notification-events.dlt")
                        .partition(0)
                        .offset(15L)
                        .build(),
                new TestingAuthenticationToken("ops-admin", "n/a", "ROLE_ADMIN")));
    }

    private void assertTrueMessage(String message) {
        assertEquals("Dry run completed. No replay was published.", message);
    }
}
