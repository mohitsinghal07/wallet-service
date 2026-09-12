package com.wallet.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestLogging extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLogging.class);
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        try { chain.doFilter(req,res); }
        finally { long micros=(System.nanoTime()-start)/1000; log.info("http.request method={} path={} status={} latency_us={}",req.getMethod(),req.getRequestURI(),res.getStatus(),micros); }
    }
}
