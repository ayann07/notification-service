package notification_service.service;

import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnreadCounterService {

    private final StringRedisTemplate redisTemplate;
    private static final String UNREAD_PREFIX = "unread:";

    // called when a new notification is saved in db
    public void increment(UUID userId) {
        if (userId == null)
            return;
        String key = UNREAD_PREFIX + userId;
        redisTemplate.opsForValue().increment(key);
        log.debug("Incremented unread count for user: {}", userId);
    }

    // called when a user clicks a single notification to mark it as read.
    public void decrement(UUID userId) {
        if (userId == null)
            return;
        String key = UNREAD_PREFIX + userId;
        Long currentCount = redisTemplate.opsForValue().decrement(key);
        if (currentCount != null && currentCount < 0)
            redisTemplate.opsForValue().set(key, "0");

        log.debug("Decremented unread count for user: {}. Current count: {}", userId, currentCount);
    }

    // called when the user clicks the "Mark all as read" button.
    public void reset(UUID userId) {
        if (userId == null)
            return;
        String key = UNREAD_PREFIX + userId;
        redisTemplate.opsForValue().set(key, "0");
        log.debug("Reset unread count to 0 for user: {}", userId);
    }

    public long getUnreadCounter(UUID userId) {
        if (userId == null)
            return 0L;
        String key = UNREAD_PREFIX + userId;
        String currentCount = redisTemplate.opsForValue().get(key);
        return currentCount != null ? Long.parseLong(currentCount) : 0L;
    }

}
