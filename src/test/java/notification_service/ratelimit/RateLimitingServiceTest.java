package notification_service.ratelimit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import notification_service.enums.DeliveryChannel;

@ExtendWith(MockitoExtension.class)
class RateLimitingServiceTest {
    // This class tests the higher-level rate-limit rules.
    // The actual Redis sliding-window algorithm lives in another class, so here we
    // only verify that the correct keys, limits, and bypass rules are used.

    @Mock
    // Fake lower-level rate limiter.
    private SlidingWindowRateLimiter slidingWindow;

    @InjectMocks
    // Real service under test with the mocked dependency injected automatically.
    private RateLimitingService rateLimitingService;

    @Test
    void isProducerAllowedUsesProducerKeyAndWindow() {
        // Arrange: pretend the lower-level limiter allows this producer.
        when(slidingWindow.isAllowed("rate:producer:BILLING", 100, Duration.ofSeconds(10))).thenReturn(true);

        // Assert: the service should return true to mean "allow this request".
        assertTrue(rateLimitingService.isProducerAllowed("BILLING"));
    }

    @Test
    void isUserEventAllowedReturnsFalseWhenLimiterBlocks() {
        UUID userId = UUID.randomUUID();

        // Arrange: the user has exceeded the event-specific rate limit.
        when(slidingWindow.isAllowed("rate:user:" + userId + ":ORDER_SHIPPED", 7, Duration.ofMinutes(10)))
                .thenReturn(false);

        assertFalse(rateLimitingService.isUserEventAllowed(userId, "ORDER_SHIPPED"));
    }

    @Test
    void isChannelAllowedBypassesPushNotificationsAndNullUsers() {
        // Push notifications and null users are treated as bypass cases in the
        // current implementation.
        assertTrue(rateLimitingService.isChannelAllowed(UUID.randomUUID(), DeliveryChannel.PUSH_NOTIFICATION));
        assertTrue(rateLimitingService.isChannelAllowed(null, DeliveryChannel.EMAIL));

        // Since these are bypass cases, the lower-level limiter should not even run.
        verify(slidingWindow, never()).isAllowed(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void isChannelAllowedUsesExpectedEmailLimit() {
        UUID userId = UUID.randomUUID();

        // EMAIL currently uses a "5 per hour" limit in the service.
        when(slidingWindow.isAllowed("rate:channel:" + userId + ":EMAIL", 5, Duration.ofHours(1))).thenReturn(true);

        assertTrue(rateLimitingService.isChannelAllowed(userId, DeliveryChannel.EMAIL));
    }

    @Test
    void isChannelAllowedUsesExpectedSmsLimit() {
        UUID userId = UUID.randomUUID();

        // SMS also uses "5 per hour" in the current implementation.
        when(slidingWindow.isAllowed("rate:channel:" + userId + ":SMS", 5, Duration.ofHours(1))).thenReturn(false);

        assertFalse(rateLimitingService.isChannelAllowed(userId, DeliveryChannel.SMS));
    }
}
