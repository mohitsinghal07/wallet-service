package com.wallet.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class AuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String correlation = req.getHeader("X-Correlation-Id");
        if (correlation == null || correlation.isBlank()) correlation = UUID.randomUUID().toString();
        String bearer = req.getHeader("Authorization");
        String user = null;
        if (bearer != null && bearer.startsWith("Bearer ")) user = bearer.substring(7).trim();
        if (user != null && !user.isBlank()) MDC.put("user_id", user);
        MDC.put("correlation_id", correlation);
        res.setHeader("X-Correlation-Id", correlation);
        try { chain.doFilter(req, res); } finally { MDC.clear(); }
    }
}
