package notification_service.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification_service.dto.NotificationEvent;
import notification_service.ratelimit.RateLimitingService;
import notification_service.service.NotificationProcessingService;

@Slf4j
// @Slf4j: This is just a shortcut so you don't have to write Logger logger =
// LoggerFactory.getLogger(...). It gives you the log variable so you can print
// things to your terminal.
@Service
// @Service: This tells Spring Boot, "When the app starts, create one instance
// of this class and keep it alive in the background permanently."
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationProcessingService processingService;
    private final RateLimitingService rateLimitingService;

    // What is this class's actual job?
    // It is just a door. It doesn't talk to the database. It doesn't send emails.
    // Its only job is to listen to the Kafka network, catch the incoming JSON, turn
    // it into a Java object, and hand it off to the next layer (the Processing
    // Service) to do the actual heavy lifting.
    @KafkaListener(topics = "notification-events", groupId = "notification-group")
    public void consumeNotificationEvent(NotificationEvent event) {
        log.info("Received Kafka Event! EventType: {} | CorrelationId: {}",
                event.getEventType(), event.getCorrelationId());

        try {
            if (!rateLimitingService.isProducerAllowed(event.getProducerName())) {
                // Send to a Dead Letter Queue (DLQ) here
                return;
            }

            if (!rateLimitingService.isUserEventAllowed(event.getUserId(), event.getEventType())) {
                return;
            }

            // Here is where we will pass the event to the processing engine
            processingService.process(event);
            log.info("Successfully processed event: {}", event.getCorrelationId());

        } catch (Exception e) {
            log.error("Failed to process event: {}. Reason: {}",
                    event.getCorrelationId(), e.getMessage());
            // In a real system, we would send this to a Dead Letter Queue (DLQ) here
        }
    }
}

/*
 * full flow
 * 
 * Phase 1: The Upstream Push (Producer)
 * Let's say the Order Service wants to trigger a notification. It creates a
 * Java object and tells its own Kafka Producer to send it.
 * 
 * The Rule: Your application.yml under producer says value-serializer:
 * JsonSerializer.
 * 
 * What Happens: The Order Service takes its Java object, crushes it down into a
 * raw JSON string like {"userId":"123", "eventType":"ORDER_SHIPPED"}, turns
 * that string into raw bytes (1s and 0s), and fires it over the network to
 * Kafka.
 * 
 * 
 * Phase 2: The Handshake (bootstrap-servers & group-id)
 * Your Notification Service boots up. Spring Boot reads your application.yml
 * and sees:
 * 
 * bootstrap-servers: localhost:9092
 * group-id: notification-group
 * 
 * What Happens: Your Spring Boot app knocks on the door at port 9092. It says,
 * "Hi Kafka, I am a consumer belonging to the notification-group." Kafka looks
 * at the notification-group ledger to see if this group has ever connected
 * before. This group ID is crucial because if you spin up 5 instances of your
 * Notification Service, Kafka uses this ID to load-balance the messages so no
 * two instances process the exact same email.
 * 
 * Phase 3: Catching Up (auto-offset-reset: earliest)
 * Kafka says,
 * "Okay, I recognize your group. But your app was offline for the last 10 minutes. What do you want me to do?"
 * 
 * The Rule: Your application.yml says auto-offset-reset: earliest.
 * 
 * What Happens: Your app tells Kafka,
 * "Please start giving me messages from the earliest one I missed, right where I left off before I crashed."
 * (If you had set this to latest, your app would ignore the missed messages and
 * only process brand new ones, meaning 10 minutes worth of users would never
 * get their emails!).
 * 
 * 
 * Phase 4: The Translation (value-deserializer & trusted.packages)
 * Kafka starts handing those raw bytes (the 1s and 0s) to your application. But
 * your Java code doesn't know how to read raw bytes.
 * 
 * The Rule: Your application.yml says value-deserializer: JsonDeserializer and
 * trusted.packages: "*".
 * 
 * What Happens: Spring Boot intercepts the bytes. The JsonDeserializer reads
 * the bytes and reconstructs the JSON string: {"userId":"123",
 * "eventType":"ORDER_SHIPPED"}.
 * Then, it looks at the @KafkaListener method signature, which asks for a
 * NotificationEvent object. Because you added trusted.packages: "*", Spring
 * Boot is legally allowed to take that JSON string and magically map the fields
 * directly into your NotificationEvent.java class.
 * 
 * 
 * Phase 5: The Execution (@KafkaListener)
 * The Rule: Your Java code has @KafkaListener(topics = "notification-events").
 * 
 * What Happens: Now that Spring Boot has a perfectly constructed, ready-to-use
 * NotificationEvent Java object, it looks for any method listening to the
 * notification-events topic. It finds your consumeNotificationEvent method,
 * passes the Java object into the parentheses (NotificationEvent event), and
 * executes the code inside your block.
 */

// Summary
// Producer turns objects into bytes and stores them in Kafka.

// bootstrap-servers connects to Kafka.

// auto-offset-reset ensures no messages are dropped if you restart.

// value-deserializer turns bytes back into a Java object.

// @KafkaListener receives the object and triggers your business logic.

// what is topics="notification-events"

// In Kafka, a Topic is essentially a named folder, a category, or a dedicated
// data pipe.

// In a massive company like Netflix or Amazon, the Kafka cluster has hundreds
// of different topics to keep data organized:

// user-login-events (Listened to by the Security Service)

// inventory-updates (Listened to by the Warehouse Service)

// payment-transactions (Listened to by the Accounting Service)

// notification-events (Listened to by your service!)

// Let's say the Warehouse Service fires a message into Kafka saying: "We just
// received 500 new iPhones." It puts this message into the inventory-updates
// topic.

// Because your @KafkaListener specifically states topics =
// "notification-events", your Notification Service completely ignores the
// iPhone message