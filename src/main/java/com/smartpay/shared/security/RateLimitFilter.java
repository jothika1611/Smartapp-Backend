package com.smartpay.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpay.shared.config.RateLimitProperties;
import com.smartpay.shared.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-process sliding-minute limiter. Phase 9 will add a Redis-backed store; this
 * implementation is the graceful fallback and is sufficient for a single-node demo.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean authPath = path.startsWith("/api/v1/auth/");
        int limit = authPath ? properties.getAuthPerMinute() : properties.getPerMinute();
        String key = (authPath ? "auth:" : "api:") + request.getRemoteAddr();
        long minute = Instant.now().getEpochSecond() / 60;

        Window window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.minute != minute) {
                return new Window(minute, 1);
            }
            existing.count++;
            return existing;
        });

        if (window.count > limit) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponse body = new ErrorResponse(429, "Too many requests, please try again shortly",
                    UUID.randomUUID().toString().substring(0, 8));
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static class Window {
        private final long minute;
        private int count;

        private Window(long minute, int count) {
            this.minute = minute;
            this.count = count;
        }
    }
}
