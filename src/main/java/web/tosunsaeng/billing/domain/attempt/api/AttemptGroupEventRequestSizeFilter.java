package web.tosunsaeng.billing.domain.attempt.api;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AttemptGroupEventRequestSizeFilter extends OncePerRequestFilter {

    private static final String PATH = "/internal/v1/attempt-group-events";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (request.getContentLengthLong() > AttemptGroupEventDecoder.MAX_PAYLOAD_BYTES) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":\"INVALID_REQUEST\","
                    + "\"message\":\"The request is invalid.\","
                    + "\"retryable\":false,"
                    + "\"correlationId\":\"" + UUID.randomUUID() + "\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
