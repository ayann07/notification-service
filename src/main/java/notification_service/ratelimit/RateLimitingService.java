package notification_service.ratelimit;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification_service.enums.DeliveryChannel;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitingService {
    private final SlidingWindowRateLimiter slidingWindow;

    public boolean isProducerAllowed(String producerName) {
        String key = "rate:producer:" + producerName;
        boolean allowed = slidingWindow.isAllowed(key, 100, Duration.ofSeconds(10));
        if (!allowed) {
            log.warn("PRODUCER BLOCKED: {} limit exceeded", producerName);
        }
        return allowed;
    }

    public boolean isUserEventAllowed(UUID userId, String eventType) {
        String key = "rate:user:" + userId + ":" + eventType;
        boolean allowed = slidingWindow.isAllowed(key, 7, Duration.ofMinutes(10));
        if (!allowed) {
            log.warn("USER BLOCKED: {} getting spammed with {} events", userId, eventType);
        }
        return allowed;
    }

    public boolean isChannelAllowed(UUID userId, DeliveryChannel deliveryChannel) {
        if (deliveryChannel == DeliveryChannel.PUSH_NOTIFICATION || userId == null)
            return true;
        String key = "rate:channel:" + userId + ":" + deliveryChannel.name();
        boolean allowed = switch (deliveryChannel) {
            case SMS -> slidingWindow.isAllowed(key, 5, Duration.ofHours(1));
            case EMAIL -> slidingWindow.isAllowed(key, 05, Duration.ofHours(1));
            case PUSH_NOTIFICATION -> slidingWindow.isAllowed(key, 20, Duration.ofMinutes(10));
            default -> true;
        };

        if (!allowed) {
            log.warn("CHANNEL BLOCKED: Channel {} blocked for user {}", deliveryChannel, userId);
        }
        return allowed;

    }
}