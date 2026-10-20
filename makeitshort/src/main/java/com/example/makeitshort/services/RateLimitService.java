package com.example.makeitshort.services;

import com.example.makeitshort.models.RateLimitData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${makeitshort.rate-limit.requests-per-minutes}")
    private int requestsPerMinute;

    @Value("${makeitshort.rate-limit.requests-per-hour}")
    private int requestsPerHour;


    private final ConcurrentHashMap<String, RateLimitData> rateLimitDay = new ConcurrentHashMap<>();

    private static final String REDIS_KEY_PREFIX = "ratelimit:";

    public boolean isAllowed(String clientIp) {
        String redisKey = REDIS_KEY_PREFIX + clientIp;

        LocalDateTime now = LocalDateTime.now();
        RateLimitData data = getRateLimitDataFromRedis(redisKey);
        if (data == null) {
            data = rateLimitDay.computeIfAbsent(clientIp, key -> RateLimitData.builder()
                    .minuteCount(0)
                    .hourCount(0)
                    .minuteWindowStart(now)
                    .hourWindowStart(now)
                    .build());
        }
        if (isWithinMinuteWindow(data, now)) {
            if (data.getMinuteCount() >= requestsPerMinute) {
                log.warn("Rate limit exceeded for {} minutes", clientIp);
                return false;
            }
        } else {
            data.setMinuteCount(0);
            data.setMinuteWindowStart(now);
        }
        if (isWithinHourWindow(data, now)) {
            if (data.getHourCount() >= requestsPerHour) {
                log.warn("Rate limit exceeded for {} minutes", clientIp);
                return false;
            }
        } else {
            data.setHourCount(0);
            data.setHourWindowStart(now);
        }
        data.setMinuteCount(data.getMinuteCount() + 1);
        data.setHourCount(data.getHourCount() + 1);
        saveRateLimitDataToRedis(redisKey, data);
        return true;
    }

    private boolean isWithinHourWindow(RateLimitData data, LocalDateTime now) {
        return data.getHourWindowStart() != null && ChronoUnit.HOURS.between(
                data.getHourWindowStart(), now
        ) < 1;
    }

    private boolean isWithinMinuteWindow(RateLimitData data, LocalDateTime now) {
        return data.getMinuteWindowStart() != null && ChronoUnit.MINUTES.between(
                data.getMinuteWindowStart(), now
        ) < 1;
    }

    private void saveRateLimitDataToRedis(String redisKey, RateLimitData data) {
        try {
            redisTemplate.opsForValue().set(redisKey, data, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to save Rate limit data to redis {}", e.getMessage());
        }
    }

    private RateLimitData getRateLimitDataFromRedis(String redisKey) {
        try {
            return (RateLimitData) redisTemplate.opsForValue().get(redisKey);
        } catch (Exception e) {
            log.warn("Failed to get rate limit data from redis: {}", e.getMessage());
            return null;
        }
    }

    public int getRemainingRequests(String clientIp) {
        String redisKey = REDIS_KEY_PREFIX + clientIp;
        RateLimitData data = getRateLimitDataFromRedis(redisKey);

        if (data == null) {
            return requestsPerMinute;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!isWithinMinuteWindow(data, now)) {
            return requestsPerMinute;
        }
        return Math.max(0, requestsPerMinute - data.getMinuteCount());
    }

    public long getTimeUntilReset(String clientIp) {
        String redisKey = REDIS_KEY_PREFIX + clientIp;
        RateLimitData data = getRateLimitDataFromRedis(redisKey);
        if (data == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        if (data.getMinuteCount() >= requestsPerMinute) {
            LocalDateTime nextMinute = data.getMinuteWindowStart()
                    .plusMinutes(1);
            return ChronoUnit.SECONDS.between(now, nextMinute);
        }
        if (data.getHourCount() >= requestsPerHour) {
            LocalDateTime nextHour = data.getHourWindowStart()
                    .plusHours(1);
            return ChronoUnit.SECONDS.between(now, nextHour);
        }
        return 0;
    }
}
