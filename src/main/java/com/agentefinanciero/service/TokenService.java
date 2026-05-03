package com.agentefinanciero.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TokenService {

    private final Cache<String, String> cache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    /** Creates a one-use token that maps to the given value. Expires in 10 minutes. */
    public String crearToken(String value) {
        String token = UUID.randomUUID().toString();
        cache.put(token, value);
        return token;
    }

    /** Returns the value and invalidates the token (single use). Returns null if expired/missing. */
    public String consumirToken(String token) {
        if (token == null) return null;
        String value = cache.getIfPresent(token);
        if (value != null) cache.invalidate(token);
        return value;
    }
}
