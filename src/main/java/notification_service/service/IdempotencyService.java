package notification_service.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final StringRedisTemplate redisTemplate;
    private static final String IDEMPOTENCY_PREFIX = "idem:";

    private static final Duration TTL = Duration.ofHours(24);

    public boolean isDuplicate(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.error("Idempotency key is missing");
            throw new IllegalArgumentException("Idempotency key is mandatory for processing notifications.");
        }

        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        Boolean isNewEvent = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", TTL);
        if (Boolean.FALSE.equals(isNewEvent)) {
            log.warn("Duplicate event detected by Redis! Dropping message with key: {}", idempotencyKey);
            return true;
        }

        log.debug("First time seeing event {}. Lock acquired in Redis.", idempotencyKey);
        return false;

    }

}
