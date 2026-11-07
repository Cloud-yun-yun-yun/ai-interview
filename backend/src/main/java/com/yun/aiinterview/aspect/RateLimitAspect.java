package com.yun.aiinterview.aspect;


import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import com.yun.aiinterview.annotation.RateLimit;
import com.yun.aiinterview.handler.BusinessException;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;


@Slf4j
@Component
@Aspect
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedissonClient redissonClient;

    // 固定窗口计数：GET 当前计数，未超限就 INCR+EXPIRE 重置窗口；超限返回 0
    private static final String FIXED_WINDOW_SCRIPT =
            "local key = KEYS[1] " +
                    "local window_ms = tonumber(ARGV[1]) " +
                    "local max_count = tonumber(ARGV[2]) " +
                    "local now_ms = tonumber(ARGV[3]) " +
                    "local member = ARGV[4] " +
                    "local window_start = now_ms - window_ms " +
                    "redis.call('ZREMRANGEBYSCORE', key, 0, window_start) " +
                    "local current_count = redis.call('ZCARD', key) " +
                    "if current_count <= max_count then " +
                    "    redis.call('ZADD', key, now_ms, member) " +
                    "    redis.call('PEXPIRE', key, window_ms + 1000) " +
                    "    return 1 " +
                    "else " +
                    "    return 0 " +
                    "end";

    @Around("@annotation(com.yun.aiinterview.annotation.RateLimit) || " +
            "@within(com.yun.aiinterview.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit[] rules = method.getAnnotationsByType(RateLimit.class);

        if (rules.length != 0) {

            String className = method.getDeclaringClass().getSimpleName();
            String methodName = method.getName();

            for (RateLimit rule : rules) {
                String prefix = rule.dimension() == RateLimit.Dimension.GLOBAL
                        ? "global:" + className + ":" + methodName
                        : "ip:" + getClientIp();
                checkLimit(prefix, rule);
            }

        }
        return joinPoint.proceed();   // 放行：继续执行原方法并返回它的结果
    }

    private void checkLimit(String dimensionPrefix, RateLimit rule) {
        String key = "ratelimit:" + dimensionPrefix;

        try {
            // 通过 Redisson 的 RScript 执行 Lua 脚本，StringCodec 保证键与参数按字符串读写
            RScript script = redissonClient.getScript(StringCodec.INSTANCE);
            String requestId = UUID.randomUUID().toString();
            Long result = script.eval(
                    RScript.Mode.READ_WRITE,
                    FIXED_WINDOW_SCRIPT,
                    RScript.ReturnType.INTEGER,
                    List.of(key),
                    String.valueOf(rule.interval()*1000),
                    String.valueOf(rule.count()),
                    String.valueOf(System.currentTimeMillis()),
                    requestId
            );
            if (result == null || result == 0L) {
                throw new BusinessException(HttpStatus.FORBIDDEN, 403, "请求过于频繁，请稍后再试");  // 超限：拒绝
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("限流脚本执行异常，key={}, error={}", key, e.getMessage(), e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 403, "限流系统异常，请稍后再试");
        }
    }

    private String getClientIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        HttpServletRequest request = attrs.getRequest();
        // 处理常见的代理转发头（和原项目一致，后端面前常有 nginx）
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        String xReal = request.getHeader("X-Real-IP");
        if (xReal != null && !xReal.isEmpty()) return xReal;
        return request.getRemoteAddr();
    }
}