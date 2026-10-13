package web.tosunsaeng.billing.global.config.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class WorkloadAuthenticationFilter extends OncePerRequestFilter {
    private final WorkloadJwtProperties properties;
    private final WorkloadJwtCodec verifier;
    WorkloadAuthenticationFilter(WorkloadJwtProperties properties, WorkloadJwtCodec verifier) { this.properties = properties; this.verifier = verifier; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        WorkloadJwtCodec.Principal principal;
        try {
            var headers = Collections.list(request.getHeaders("Authorization"));
            if (headers.size() != 1 || !headers.getFirst().startsWith("Bearer ") || request.getQueryString() != null) throw new WorkloadJwtCodec.InvalidToken();
            principal = verifier.authenticate(headers.getFirst().substring(7));
        } catch (WorkloadJwtCodec.InvalidToken invalid) { WorkloadIngressFilter.reject(response, 401); return; }
        String purpose = WorkloadIngressFilter.ROUTES.get(request.getRequestURI());
        if (!"POST".equals(request.getMethod()) || purpose == null || !"identity-service".equals(principal.subject())
                || !("tosunsaeng-billing:" + purpose + ":" + properties.getEnvironment()).equals(principal.audience())) {
            WorkloadIngressFilter.reject(response, 403); return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("identity-service", null, List.of(new SimpleGrantedAuthority("ROLE_IDENTITY_WORKLOAD"))));
        SecurityContextHolder.setContext(context);
        try { chain.doFilter(request, response); } finally { SecurityContextHolder.clearContext(); }
    }
}
