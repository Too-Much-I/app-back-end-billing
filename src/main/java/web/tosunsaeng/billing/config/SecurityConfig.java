package web.tosunsaeng.billing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties({
        InternalIngressProperties.class,
        TrialEligibilityProperties.class,
        ReservationProperties.class,
        BillingMongoProperties.class
})
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            InternalIngressProperties ingressProperties,
            TrialEligibilityProperties trialEligibilityProperties,
            BillingMongoProperties mongoProperties,
            Environment environment
    ) throws Exception {
        validateConfiguration(
                ingressProperties, trialEligibilityProperties, mongoProperties, environment
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
                        authorize.requestMatchers(
                                HttpMethod.POST,
                                "/internal/v1/reservations",
                                "/internal/v1/reservations/status",
                                "/internal/v1/reservations/*/confirm",
                                "/internal/v1/reservations/*/cancel"
                        ).hasRole("LEARNING_CORE_WORKLOAD");
                    } else if (ingressProperties.getMode()
                            == InternalIngressProperties.Mode.LATTICE_AWS_IAM) {
                        authorize.requestMatchers(
                                HttpMethod.POST,
                                "/internal/v1/eligibility/trial/events"
                        ).permitAll();
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
            TrialEligibilityProperties eligibility,
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
