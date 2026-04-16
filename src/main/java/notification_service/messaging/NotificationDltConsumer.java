package notification_service.messaging;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import notification_service.dto.NotificationEvent;

@Slf4j
@Service
public class NotificationDltConsumer {

    // This consumer is intentionally simple: it gives us an operational place to
    // observe failed records after retries are exhausted.
    @KafkaListener(topics = "${app.kafka.topics.notification-events-dlt:notification-events.dlt}", groupId = "notification-dlt-group")
    public void consumeDeadLetterRecord(ConsumerRecord<String, NotificationEvent> record) {
        NotificationEvent event = record.value();

        log.error(
                "Received record in DLT. topic={} partition={} offset={} key={} correlationId={} eventType={} headers={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                event != null ? event.getCorrelationId() : null,
                event != null ? event.getEventType() : null,
                readableHeaders(record));
    }

    private Map<String, String> readableHeaders(ConsumerRecord<String, NotificationEvent> record) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Header header : record.headers()) {
            headers.put(header.key(), header.value() == null ? "null" : new String(header.value(), StandardCharsets.UTF_8));
        }
        return headers;
    }
}
