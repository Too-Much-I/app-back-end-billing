package web.tosunsaeng.billing.global.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.domain.attempt.config.AttemptGroupEventProperties;
import web.tosunsaeng.billing.domain.reservation.config.ReservationProperties;
import web.tosunsaeng.billing.domain.ownerrebind.config.OwnerRebindProperties;
import web.tosunsaeng.billing.global.config.mongodb.BillingMongoProperties;

@Configuration
@EnableConfigurationProperties({
        InternalIngressProperties.class,
        AttemptGroupEventProperties.class,
        TrialEligibilityProperties.class,
        ReservationProperties.class,
        OwnerRebindProperties.class,
        BillingMongoProperties.class
})
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            InternalIngressProperties ingressProperties,
            AttemptGroupEventProperties attemptGroupEventProperties,
            TrialEligibilityProperties trialEligibilityProperties,
            OwnerRebindProperties ownerRebindProperties,
            BillingMongoProperties mongoProperties,
            Environment environment
    ) throws Exception {
        validateConfiguration(
                ingressProperties, attemptGroupEventProperties,
                trialEligibilityProperties, ownerRebindProperties,
                mongoProperties, environment
        );
        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    if (ingressProperties.getMode() == InternalIngressProperties.Mode.TEST) {
                        authorize.requestMatchers(
                                HttpMethod.POST,
                                "/internal/v1/eligibility/trial/events"
                        ).hasRole("IDENTITY_WORKLOAD");
                        if (ownerRebindProperties.isEnabled()) {
                            authorize.requestMatchers(
                                    HttpMethod.POST,
                                    "/internal/v1/eligibility/trial/owner/events",
                                    "/internal/v1/owners/merge/events"
                            ).hasRole("IDENTITY_WORKLOAD");
                            authorize.requestMatchers(
                                    HttpMethod.POST,
                                    "/internal/v1/reservations/continuations/phone"
                            ).hasRole("LEARNING_CORE_WORKLOAD");
                        }
                        authorize.requestMatchers(
                                HttpMethod.POST,
                                "/internal/v1/reservations",
                                "/internal/v1/reservations/status",
                                "/internal/v1/reservations/*/confirm",
                                "/internal/v1/reservations/*/cancel"
                        ).hasRole("LEARNING_CORE_WORKLOAD");
                        if (attemptGroupEventProperties.isEnabled()) {
                            authorize.requestMatchers(
                                    HttpMethod.POST,
                                    "/internal/v1/attempt-group-events"
                            ).hasRole("LEARNING_CORE_WORKLOAD");
                        }
                    } else if (ingressProperties.getMode()
                            == InternalIngressProperties.Mode.LATTICE_AWS_IAM) {
                        authorize.requestMatchers(
                                HttpMethod.POST,
                                "/internal/v1/eligibility/trial/events"
                        ).permitAll();
                        if (ownerRebindProperties.isEnabled()) {
                            authorize.requestMatchers(
                                    HttpMethod.POST,
                                    "/internal/v1/eligibility/trial/owner/events",
                                    "/internal/v1/owners/merge/events"
                            ).permitAll();
                            authorize.requestMatchers(
                                    HttpMethod.POST,
                                    "/internal/v1/reservations/continuations/phone"
                            ).permitAll();
                        }
                        if (attemptGroupEventProperties.isEnabled()) {
                            authorize.requestMatchers(
                                    HttpMethod.POST,
                                    "/internal/v1/attempt-group-events"
                            ).permitAll();
                        }
                        authorize.requestMatchers(
                                HttpMethod.POST,
                                "/internal/v1/reservations",
                                "/internal/v1/reservations/status",
                                "/internal/v1/reservations/*/confirm",
                                "/internal/v1/reservations/*/cancel"
                        ).permitAll();
                    }
                    authorize.anyRequest().denyAll();
                })
                .build();
    }

    private static void validateConfiguration(
            InternalIngressProperties ingress,
            AttemptGroupEventProperties attemptGroupEvents,
            TrialEligibilityProperties eligibility,
            OwnerRebindProperties ownerRebind,
            BillingMongoProperties mongo,
            Environment environment
    ) {
        if (ingress.getMode() == InternalIngressProperties.Mode.TEST
                && !environment.acceptsProfiles(Profiles.of("test"))) {
            throw new IllegalStateException("Internal test ingress requires the test profile.");
        }
        if (ingress.getMode() != InternalIngressProperties.Mode.DISABLED
                && eligibility.getExpectedConsumerScopeId().isBlank()) {
            throw new IllegalStateException("Expected eligibility consumer scope is required.");
        }
        if (attemptGroupEvents.isEnabled()
                && (attemptGroupEvents.getMaxFutureSkew() == null
                || attemptGroupEvents.getMaxFutureSkew().isNegative())) {
            throw new IllegalStateException("AttemptGroup future clock skew must be non-negative.");
        }
        if ((ownerRebind.isEnabled() || ownerRebind.isCleanupEnabled())
                && (ownerRebind.getMaxFutureSkew() == null
                || ownerRebind.getMaxFutureSkew().isNegative()
                || ownerRebind.getInboxRetention() == null
                || ownerRebind.getInboxRetention().isNegative()
                || ownerRebind.getInboxRetention().isZero()
                || ownerRebind.getLegacyFenceRetention() == null
                || ownerRebind.getLegacyFenceRetention().isNegative()
                || ownerRebind.getLegacyFenceRetention().isZero()
                || ownerRebind.getCleanupScanInterval() == null
                || ownerRebind.getCleanupScanInterval().isNegative()
                || ownerRebind.getCleanupScanInterval().isZero()
                || ownerRebind.getCleanupScanInterval().compareTo(java.time.Duration.ofHours(1)) > 0
                || ownerRebind.getCleanupBatchSize() < 1
                || ownerRebind.getMaxSubjectsPerEvent() != 100)) {
            throw new IllegalStateException("Owner rebind configuration is invalid.");
        }
        if (ingress.getMode() == InternalIngressProperties.Mode.LATTICE_AWS_IAM) {
            boolean validEnvironment = "production".equals(ingress.getEnvironment())
                    || "staging".equals(ingress.getEnvironment());
            if (!ingress.isLatticeOnly() || !validEnvironment
                    || !mongo.isInitializeIndexes() || !mongo.isRequireTransactions()) {
                throw new IllegalStateException(
                        "Lattice ingress requires an isolated environment and transactional MongoDB."
                );
            }
        }
    }
}
