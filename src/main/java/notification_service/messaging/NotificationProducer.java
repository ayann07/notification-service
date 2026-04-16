package notification_service.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification_service.dto.NotificationEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    @Value("${app.kafka.topics.notification-events:notification-events}")
    private String notificationEventsTopic;

    public void publishEvent(NotificationEvent event) {
        log.info("Publishing test event into the kafka...");
        // This pushes the Java object into the "notification-events" topic
        kafkaTemplate.send(notificationEventsTopic, event.getUserId().toString(), event);
    }

}
