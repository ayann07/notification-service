package notification_service.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import notification_service.dto.NotificationEvent;
import notification_service.service.NotificationProducerService;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestEventController {
    private final NotificationProducerService producerService;

    @PostMapping("/publish")
    public ResponseEntity<String> publishTestEvent(@RequestBody NotificationEvent event) {
        if (event.getUserId() == null)
            event.setUserId(UUID.randomUUID());
        if (event.getCorrelationId() == null)
            event.setCorrelationId("test-" + UUID.randomUUID());

        producerService.publishEvent(event);
        return ResponseEntity.ok("Successfully pushed to Kafka! Check your terminal and your phone/email");
    }

}
