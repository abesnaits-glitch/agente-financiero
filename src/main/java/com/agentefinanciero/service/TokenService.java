package com.agentefinanciero.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TokenService {

    // Dashboard tokens: 10-minute TTL
    private final Cache<String, String> cache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    // PDF report tokens: 30-minute TTL (time to open after requesting)
    private final Cache<String, String> cacheExtendido = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(1_000)
            .build();

    /** Creates a one-use token (10 min TTL). */
    public String crearToken(String value) {
        String token = UUID.randomUUID().toString();
        cache.put(token, value);
        return token;
    }

    /** Creates a one-use token with 30-minute TTL (for PDF downloads). */
    public String crearTokenExtendido(String value) {
        String token = UUID.randomUUID().toString();
        cacheExtendido.put(token, value);
        return token;
    }

    /** Returns the value and invalidates the token (single use). Returns null if expired/missing. */
    public String consumirToken(String token) {
        if (token == null) return null;
        String value = cache.getIfPresent(token);
        if (value != null) { cache.invalidate(token); return value; }
        value = cacheExtendido.getIfPresent(token);
        if (value != null) { cacheExtendido.invalidate(token); return value; }
        return null;
    }
}
