package com.foliolens.backend.global.web;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    private static final String MDC_KEY = "requestId";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = normalize(request.getHeader(HEADER_NAME));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    public static String currentRequestId() {
        String requestId = MDC.get(MDC_KEY);
        return requestId == null ? UUID.randomUUID().toString() : requestId;
    }

    public static <T> T withRequestId(String requestId, Supplier<T> action) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable(
                MDC_KEY,
                normalize(requestId))) {
            return action.get();
        }
    }

    private static String normalize(String requestId) {
        if (requestId != null && SAFE_REQUEST_ID.matcher(requestId).matches()) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }
}
