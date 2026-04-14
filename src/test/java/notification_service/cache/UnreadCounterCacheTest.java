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

// Activates the Mockito framework so the @Mock annotations work.
@ExtendWith(MockitoExtension.class)
class UnreadCounterCacheTest {

    // --- MOCKS ---
    // We are mocking the core Spring Redis template. We don't want to connect
    // to a real Redis server (like localhost:6379) during our tests.
    @Mock
    private StringRedisTemplate redisTemplate;

    // In Spring Redis, you don't usually call methods directly on the template.
    // You call redisTemplate.opsForValue() to get a ValueOperations object, and
    // call methods on THAT. Therefore, we must mock this secondary object as well.
    @Mock
    private ValueOperations<String, String> valueOperations;

    // This is the actual service we are testing. (Notice it does NOT have @Mock).
    private UnreadCounterCache unreadCounterCache;

    // @BeforeEach tells JUnit: "Run this exact block of code before EVERY single
    // @Test."
    // It guarantees every test starts with a fresh, clean slate, preventing data
    // from
    // one test from bleeding into another and causing random failures.
    @BeforeEach
    void setUp() {
        // --- ARRANGE (Common Setup) ---
        // We initialize the cache and inject our fake Redis template into it.
        unreadCounterCache = new UnreadCounterCache(redisTemplate);

        // This is a crucial Mockito trick for chained methods!
        // We are telling the fake redisTemplate: "If the code ever asks you for
        // opsForValue(),
        // don't return null. Return our fake valueOperations mock instead."
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void incrementDoesNothingForNullUserId() {
        // --- ACT ---
        unreadCounterCache.increment(null);

        // --- ASSERT ---
        // verify(..., never()) checks that an action strictly did NOT happen.
        // ArgumentMatchers.anyString() is a Mockito wildcard.
        // We are saying: "Verify that valueOperations.increment() was NEVER called
        // with ANY string whatsoever." This proves our null check is working.
        verify(valueOperations, never()).increment(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void incrementUsesUserSpecificRedisKey() {
        // --- ARRANGE ---
        UUID userId = UUID.randomUUID();

        // --- ACT ---
        unreadCounterCache.increment(userId);

        // --- ASSERT ---
        // We verify that the cache constructed the exact correct string key
        // ("unread:uuid-goes-here") before pushing it to the Redis increment command.
        verify(valueOperations).increment("unread:" + userId);
    }

    @Test
    void decrementClampsCounterToZeroWhenRedisGoesNegative() {
        // --- ARRANGE ---
        UUID userId = UUID.randomUUID();

        // Here we test an "edge case" (what happens when things go wrong).
        // We program our fake Redis to simulate a weird state: "When you decrement this
        // key,
        // pretend the result in the database is now -1."
        when(valueOperations.decrement("unread:" + userId)).thenReturn(-1L);

        // --- ACT ---
        unreadCounterCache.decrement(userId);

        // --- ASSERT ---
        // We verify that our service caught the negative number and immediately fired
        // a "set" command to force the value back to "0", protecting our data
        // integrity.
        verify(valueOperations).set("unread:" + userId, "0");
    }

    @Test
    void resetWritesZeroToRedis() {
        // --- ARRANGE ---
        UUID userId = UUID.randomUUID();

        // --- ACT ---
        unreadCounterCache.reset(userId);

        // --- ASSERT ---
        // Simply verify that resetting fires a strict overwrite command setting the key
        // to "0".
        verify(valueOperations).set("unread:" + userId, "0");
    }

    @Test
    void getUnreadCounterReturnsParsedValueOrZero() {
        // --- ARRANGE ---
        UUID userId = UUID.randomUUID();

        // We program the mock to simulate a successful cache hit.
        // "When asked to get this specific key, return the string '7'."
        when(valueOperations.get("unread:" + userId)).thenReturn("7");

        // --- ACT & ASSERT ---
        // Test 1: Check if the string "7" is correctly parsed into a Long 7L.
        assertEquals(7L, unreadCounterCache.getUnreadCounter(userId));

        // Test 2: Ensure that passing a null ID safely returns 0L instead of crashing
        // with a NullPointerException.
        assertEquals(0L, unreadCounterCache.getUnreadCounter(null));
    }
}