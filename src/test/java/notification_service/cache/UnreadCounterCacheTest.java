package notification_service.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class UnreadCounterCacheTest {
    // This class tests the small Redis cache that stores a user's unread
    // notification count.
    // We mock Redis so we can focus on the key/value behavior only.

    @Mock
    // Fake Redis template.
    private StringRedisTemplate redisTemplate;

    @Mock
    // Fake Redis string operations.
    private ValueOperations<String, String> valueOperations;

    private UnreadCounterCache unreadCounterCache;

    @BeforeEach
    void setUp() {
        // Create the real cache class with mocked Redis dependencies.
        unreadCounterCache = new UnreadCounterCache(redisTemplate);

        // Whenever the cache asks for string value operations, return the mock.
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void incrementDoesNothingForNullUserId() {
        // If userId is null, the cache should return early instead of building an
        // invalid Redis key.
        unreadCounterCache.increment(null);

        // verify(..., never()) means "make sure this method was NOT called".
        verify(valueOperations, never()).increment(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void incrementUsesUserSpecificRedisKey() {
        UUID userId = UUID.randomUUID();

        // Act: increment the unread counter for a specific user.
        unreadCounterCache.increment(userId);

        // Assert: the cache should write to the key format unread:<userId>.
        verify(valueOperations).increment("unread:" + userId);
    }

    @Test
    void decrementClampsCounterToZeroWhenRedisGoesNegative() {
        UUID userId = UUID.randomUUID();

        // Simulate Redis returning -1 after decrementing, which means the count
        // accidentally went below zero.
        when(valueOperations.decrement("unread:" + userId)).thenReturn(-1L);

        unreadCounterCache.decrement(userId);

        // The cache protects against negative unread counts by forcing them back to 0.
        verify(valueOperations).set("unread:" + userId, "0");
    }

    @Test
    void resetWritesZeroToRedis() {
        UUID userId = UUID.randomUUID();

        // reset(...) means "mark all notifications as read", so the count becomes 0.
        unreadCounterCache.reset(userId);

        verify(valueOperations).set("unread:" + userId, "0");
    }

    @Test
    void getUnreadCounterReturnsParsedValueOrZero() {
        UUID userId = UUID.randomUUID();

        // Simulate an unread count already stored in Redis.
        when(valueOperations.get("unread:" + userId)).thenReturn("7");

        // The cache converts the Redis string back into a long.
        assertEquals(7L, unreadCounterCache.getUnreadCounter(userId));

        // Null user IDs are treated safely and return 0 instead of failing.
        assertEquals(0L, unreadCounterCache.getUnreadCounter(null));
    }
}
