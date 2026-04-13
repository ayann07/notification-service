package notification_service.cache;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class IdempotencyCacheTest {
    // This class tests the Redis-backed idempotency guard.
    // "Idempotency" means: if the exact same event arrives twice, we should process
    // it only once.

    @Mock
    // Fake Redis template. Mockito creates a dummy object for us.
    private StringRedisTemplate redisTemplate;

    @Mock
    // Fake Redis value operations. This is what the service uses internally after
    // calling redisTemplate.opsForValue().
    private ValueOperations<String, String> valueOperations;

    private IdempotencyCache idempotencyCache;

    @BeforeEach
    void setUp() {
        // Create the real class we want to test, but inject mocked Redis
        // dependencies instead of a real Redis connection.
        idempotencyCache = new IdempotencyCache(redisTemplate);

        // When the code asks Redis for "value operations", return our mocked helper.
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void isDuplicateThrowsForBlankKey() {
        // Arrange + Act + Assert:
        // A blank idempotency key is invalid input, so the service should throw an
        // IllegalArgumentException immediately.
        assertThrows(IllegalArgumentException.class, () -> idempotencyCache.isDuplicate(" "));
    }

    @Test
    void isDuplicateReturnsFalseForFirstTimeEvent() {
        // Arrange:
        // Redis returns true from setIfAbsent(...) when it successfully creates the
        // key for the first time.
        when(valueOperations.setIfAbsent(eq("idem:key-1"), eq("1"), any(Duration.class))).thenReturn(true);

        // Assert:
        // false means "this is NOT a duplicate, process it normally".
        assertFalse(idempotencyCache.isDuplicate("key-1"));
    }

    @Test
    void isDuplicateReturnsTrueWhenRedisAlreadyHasKey() {
        // Arrange:
        // Redis returns false from setIfAbsent(...) when the key already exists.
        when(valueOperations.setIfAbsent(eq("idem:key-1"), eq("1"), any(Duration.class))).thenReturn(false);

        // Assert:
        // true means "this IS a duplicate, drop it".
        assertTrue(idempotencyCache.isDuplicate("key-1"));
    }
}
