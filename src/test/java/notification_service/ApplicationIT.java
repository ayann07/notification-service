package notification_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import notification_service.delivery.EmailSender;
import notification_service.dto.GuestUserDetails;
import notification_service.dto.NotificationEvent;
import notification_service.dto.TemplateRequestDTO;
import notification_service.dto.TemplateResponseDTO;
import notification_service.enums.DeliveryChannel;
import notification_service.enums.NetworkDeliveryStatus;
import notification_service.enums.RecipientType;
import notification_service.model.Notification;
import notification_service.repository.NotificationRepository;
import notification_service.security.JwtTokenService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ApplicationIT {
    // Full-stack integration test:
    // HTTP + Spring Security + Kafka + Redis idempotency/rate-limit state +
    // PostgreSQL persistence. The only external boundary we replace is AWS SES.

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoSpyBean
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        doNothing().when(emailSender).send(any(Notification.class));
    }

    @Test
    void publishesKafkaEventAndPersistsHydratedEmailNotification() {
        String uniqueSuffix = UUID.randomUUID().toString();
        String eventType = "ORDER_SHIPPED_" + uniqueSuffix;
        String idempotencyKey = "idem-" + uniqueSuffix;
        String correlationId = "corr-" + uniqueSuffix;

        TemplateRequestDTO templateRequest = new TemplateRequestDTO();
        templateRequest.setEventType(eventType);
        templateRequest.setDeliveryChannel(DeliveryChannel.EMAIL);
        templateRequest.setTitle("Order {orderId} shipped");
        templateRequest.setBody("Hello {firstName}, your order {orderId} is on the way.");
        templateRequest.setDefaultPriority(3);

        ResponseEntity<TemplateResponseDTO> templateResponse = exchange(
                HttpMethod.POST,
                "/api/v1/templates",
                templateRequest,
                TemplateResponseDTO.class,
                tokenWithRole("ROLE_ADMIN"));

        assertEquals(HttpStatus.OK, templateResponse.getStatusCode());
        assertEquals(eventType, templateResponse.getBody().getEventType());

        NotificationEvent event = NotificationEvent.builder()
                .producerName("integration-test")
                .recipientType(RecipientType.GUEST)
                .guestUserDetails(GuestUserDetails.builder()
                        .firstName("Ayan")
                        .lastName("Raza")
                        .email("ayan.integration@example.com")
                        .phoneNumber("+10000000000")
                        .build())
                .eventType(eventType)
                .correlationId(correlationId)
                .idempotencyKey(idempotencyKey)
                .metadata(Map.of("orderId", "ORD-123"))
                .build();

        ResponseEntity<String> publishResponse = exchange(
                HttpMethod.POST,
                "/api/v1/test/publish",
                event,
                String.class,
                tokenWithRole("ROLE_INTERNAL"));

        assertEquals(HttpStatus.OK, publishResponse.getStatusCode());

        Notification notification = waitForDeliveredNotification(idempotencyKey, DeliveryChannel.EMAIL);

        assertEquals(correlationId, notification.getCorrelationId());
        assertEquals(eventType, notification.getEventType());
        assertEquals("integration-test", notification.getProducerName());
        assertEquals("ayan.integration@example.com", notification.getRecipientEmail());
        assertEquals("Order ORD-123 shipped", notification.getTitle());
        assertEquals("Hello Ayan, your order ORD-123 is on the way.", notification.getMessage());
        assertEquals(NetworkDeliveryStatus.SENT, notification.getNetworkDeliveryStatus());
        assertEquals("ORD-123", notification.getMetadata().get("orderId"));
        assertEquals("Ayan", notification.getMetadata().get("firstName"));

        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey("idem:" + idempotencyKey)));
        verify(emailSender, timeout(10_000)).send(any(Notification.class));
    }

    private <T> ResponseEntity<T> exchange(
            HttpMethod method,
            String path,
            Object body,
            Class<T> responseType,
            String token) {

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(
                "http://localhost:" + port + path,
                method,
                new HttpEntity<>(body, headers),
                responseType);
    }

    private String tokenWithRole(String role) {
        return jwtTokenService.generateToken(UUID.randomUUID().toString(), List.of(role), "it@example.com");
    }

    private Notification waitForDeliveredNotification(String idempotencyKey, DeliveryChannel channel) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();

        while (System.nanoTime() < deadline) {
            Optional<Notification> notification = notificationRepository
                    .findByIdempotencyKeyAndDeliveryChannel(idempotencyKey, channel);
            if (notification.isPresent()
                    && notification.get().getNetworkDeliveryStatus() == NetworkDeliveryStatus.SENT) {
                return notification.get();
            }

            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for notification", ex);
            }
        }

        throw new AssertionError("Notification was not delivered for idempotency key " + idempotencyKey);
    }

}
