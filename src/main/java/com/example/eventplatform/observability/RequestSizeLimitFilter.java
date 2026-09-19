package com.example.eventplatform.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejeita corpos grandes antes da desserialização (proteção contra abuso de memória).
 * O gateway/Ingress deve aplicar o mesmo limite; esta é a defesa em profundidade.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public RequestSizeLimitFilter(@Value("${platform.http.max-request-bytes:4194304}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "corpo excede " + maxBytes + " bytes");
            return;
        }
        chain.doFilter(request, response);
    }
}
