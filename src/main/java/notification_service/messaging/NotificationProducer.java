package notification_service.messaging;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification_service.dto.NotificationEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishEvent(NotificationEvent event) {
        log.info("Publishing test event into the kafka...");
        // This pushes the Java object into the "notification-events" topic
        kafkaTemplate.send("notification-events", event.getUserId().toString(), event);
    }

}
