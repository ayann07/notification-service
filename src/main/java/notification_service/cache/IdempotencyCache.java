package notification_service.cache;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyCache {
    private final StringRedisTemplate redisTemplate;
    private static final String IDEMPOTENCY_PREFIX = "idem:";
    // Time-To-Live (TTL). This tells Redis: "Automatically delete this key from
    // RAM after 24 hours so the server doesn't run out of memory."
    private static final Duration TTL = Duration.ofHours(24);

    public boolean isDuplicate(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.error("Idempotency key is missing");
            throw new IllegalArgumentException("Idempotency key is mandatory for processing notifications.");
        }

        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;

        // opsForValue() targets simple String values in Redis.
        // setIfAbsent() executes the Redis 'SETNX' (SET if Not eXists) command.
        // This is 100% atomic. It checks if the key exists AND saves it in one single
        // motion. The "1" is just a dummy value; we only care that the key exists.
        Boolean isNewEvent = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", TTL);

        // If setIfAbsent returns FALSE, it means the key "idem:txn_123" was already
        // in Redis. We safely use Boolean.FALSE.equals() to avoid NullPointerExceptions
        // just in case the Redis server connection drops and returns null.
        if (Boolean.FALSE.equals(isNewEvent)) {
            log.warn("Duplicate event detected by Redis! Dropping message with key: {}", idempotencyKey);
            return true;
        }

        log.debug("First time seeing event {}. Lock acquired in Redis.", idempotencyKey);
        return false;

    }

}
