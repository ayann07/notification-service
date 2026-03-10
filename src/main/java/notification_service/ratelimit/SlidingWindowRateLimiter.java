package notification_service.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * A highly performant Sliding Window Rate Limiter using Redis Sorted Sets
 * (ZSET).
 * This ensures exact rate limiting by tracking the precise timestamp of every
 * request.
 */
@Service
@RequiredArgsConstructor
public class SlidingWindowRateLimiter {
    private final RedisTemplate<?, ?> redisTemplate;

    /**
     * Checks if a specific action is allowed based on the rate limit rules.
     *
     * @param key         The unique Redis key for this limit (e.g.,
     *                    "rate:channel:user123:SMS")
     * @param maxRequests The maximum number of allowed requests in the given window
     * @param window      The duration of the sliding window (e.g., 1 hour)
     * @return true if the request is allowed, false if the rate limit is exceeded
     */
    public boolean isAllowed(String key, int maxRequests, Duration window) {
        // Calculate our time boundaries
        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();

        // Create a unique ticket for this exact request.
        // We append a UUID so that if two requests arrive at the exact same
        // millisecond,
        // Redis doesn't treat them as identical and overwrite one.
        String member = now + "-" + UUID.randomUUID().toString();

        // We use a RedisCallback to drop down to the raw database connection.
        RedisCallback<Object> pipelineCallback = connection -> {

            // rawKey:What it is: The byte-array version of our Redis key (e.g.,
            // "rate:channel:Ayan:SMS").

            // Why we pass it: Redis can hold millions of different rate-limit lists at the
            // same time. We have to tell Redis exactly which specific user's clipboard we
            // want to look at.
            byte[] rawKey = key.getBytes();
            byte[] rawMember = member.getBytes();

            // Look at the "score" (timestamp) of all entries. Delete any that are older
            // than windowStart.
            connection.zSetCommands().zRemRangeByScore(rawKey, 0, windowStart);

            // Add the current request to the set, using the current timestamp as its score
            connection.zSetCommands().zAdd(rawKey, now, rawMember);

            // Set an expiration on the entire Redis key. If this user doesn't make another
            // request within the window duration, Redis automatically deletes the key to
            // save RAM.
            // This places a countdown timer on the entire Redis key.
            connection.keyCommands().expire(rawKey, window.getSeconds());

            // Count how many requests are currently sitting inside the valid time window.
            connection.zSetCommands().zCard(rawKey);

            // Must return null when using pipelining
            return null;
        };

        // Execute the Pipeline
        // This bundles all 4 commands into a single network request to the Redis
        // server,
        // which makes this algorithm incredibly fast and prevents network bottlenecks.
        List<Object> results = redisTemplate.executePipelined(pipelineCallback);

        // 'results' contains a list of answers for the 4 commands we ran above.
        // We only care about the answer to the 4th command (zCard), which is the total
        // count.
        Long count = (Long) results.get(results.size() - 1);

        // If the count is within our limit, allow it. Otherwise, block it.
        return count != null && count <= maxRequests;
    }
}
