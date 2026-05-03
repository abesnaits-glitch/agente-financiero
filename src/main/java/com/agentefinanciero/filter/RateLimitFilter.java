package com.agentefinanciero.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // Buckets per "ip:endpoint" key, evicted after 10 min of inactivity
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(100_000)
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path   = request.getRequestURI();
        String method = request.getMethod();

        // Skip: signature-validated webhooks, password-protected admin, static assets
        if (path.startsWith("/webhook/") || path.startsWith("/admin/")
                || path.startsWith("/images/") || path.startsWith("/reports/")) {
            chain.doFilter(request, response);
            return;
        }

        Bandwidth limit = selectLimit(path, method);
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }

        String ip  = extractIp(request);
        String key = ip + ":" + path + ":" + method.toUpperCase();
        Bucket bucket = buckets.get(key, k -> Bucket.builder().addLimit(limit).build());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("[RateLimit] 429 ip='{}' path='{}' method='{}'", ip, path, method);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Demasiadas peticiones. Espera un momento.\"}");
        }
    }

    private Bandwidth selectLimit(String path, String method) {
        // Tightest limit: subscription creation (risk of MP preference spam)
        if (path.startsWith("/api/create-subscription"))
            return Bandwidth.classic(3, Refill.intervally(3, Duration.ofMinutes(1)));

        // Medium: comment submission and launch notification (spam vectors)
        if (path.equals("/api/comentarios") && "POST".equalsIgnoreCase(method))
            return Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
        if (path.startsWith("/api/notificar-lanzamiento"))
            return Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));

        // General: all other public API + token-based dashboard
        if (path.startsWith("/api/") || path.startsWith("/dashboard/"))
            return Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1)));

        return null;
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].strip();
        return request.getRemoteAddr();
    }
}
