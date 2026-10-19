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
    private final WorkloadJwtVerifierRegistry verifiers;
    WorkloadAuthenticationFilter(WorkloadJwtProperties properties, WorkloadJwtVerifierRegistry verifiers) {
        this.properties = properties; this.verifiers = verifiers;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        WorkloadJwtVerifierRegistry.Verified verified;
        try {
            var headers = Collections.list(request.getHeaders("Authorization"));
            if (headers.size() != 1 || !headers.getFirst().startsWith("Bearer ") || request.getQueryString() != null) throw new WorkloadJwtCodec.InvalidToken();
            verified = verifiers.authenticate(headers.getFirst().substring(7));
        } catch (WorkloadJwtCodec.InvalidToken invalid) { WorkloadIngressFilter.reject(response, 401); return; }
        String identityPurpose = WorkloadIngressFilter.ROUTES.get(request.getRequestURI());
        String lcPurpose = WorkloadIngressFilter.learningCorePurpose(request.getRequestURI());
        String expectedService = identityPurpose != null ? "identity-service" : lcPurpose != null ? "learning-core-service" : null;
        String purpose = identityPurpose != null ? identityPurpose : lcPurpose;
        var principal = verified.principal();
        if (!"POST".equals(request.getMethod()) || expectedService == null || !expectedService.equals(verified.service())
                || !expectedService.equals(principal.subject())
                || !("tosunsaeng-billing:" + purpose + ":" + properties.getEnvironment()).equals(principal.audience())) {
            WorkloadIngressFilter.reject(response, 403); return;
        }
        String role = "identity-service".equals(expectedService) ? "ROLE_IDENTITY_WORKLOAD" : "ROLE_LEARNING_CORE_WORKLOAD";
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(expectedService, null, List.of(new SimpleGrantedAuthority(role))));
        SecurityContextHolder.setContext(context);
        try { chain.doFilter(request, response); } finally { SecurityContextHolder.clearContext(); }
    }
}
