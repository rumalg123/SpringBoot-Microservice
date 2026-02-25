package com.rumal.personalization_service.config;

import com.rumal.personalization_service.exception.DownstreamHttpException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CircuitBreakerConfig {

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> personalizationCircuitBreakerCustomizer(
            @Value("${resilience.circuit-breaker.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${resilience.circuit-breaker.slow-call-rate-threshold:70}") float slowCallRateThreshold,
            @Value("${resilience.circuit-breaker.slow-call-duration-ms:5000}") long slowCallDurationMs,
            @Value("${resilience.circuit-breaker.sliding-window-size:20}") int slidingWindowSize,
            @Value("${resilience.circuit-breaker.minimum-number-of-calls:10}") int minimumNumberOfCalls,
            @Value("${resilience.circuit-breaker.wait-open-ms:15000}") long waitOpenMs,
            @Value("${resilience.circuit-breaker.permitted-half-open-calls:5}") int permittedHalfOpenCalls,
            @Value("${resilience.time-limiter.timeout-ms:6000}") long timeoutMs
    ) {
        return factory -> {
            var cbConfig = io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                    .failureRateThreshold(failureRateThreshold)
                    .slowCallRateThreshold(slowCallRateThreshold)
                    .slowCallDurationThreshold(Duration.ofMillis(Math.max(1, slowCallDurationMs)))
                    .slidingWindowType(SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(Math.max(5, slidingWindowSize))
                    .minimumNumberOfCalls(Math.max(2, minimumNumberOfCalls))
                    .waitDurationInOpenState(Duration.ofMillis(Math.max(1000, waitOpenMs)))
                    .permittedNumberOfCallsInHalfOpenState(Math.max(1, permittedHalfOpenCalls))
                    .recordExceptions(Exception.class)
                    .ignoreExceptions(DownstreamHttpException.class, IllegalArgumentException.class)
                    .build();

            var tlConfig = TimeLimiterConfig.custom()
                    .timeoutDuration(Duration.ofMillis(Math.max(500, timeoutMs)))
                    .cancelRunningFuture(true)
                    .build();

            factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(cbConfig)
                    .timeLimiterConfig(tlConfig)
                    .build());
        };
    }

    @Bean
    public RetryRegistry personalizationRetryRegistry(
            @Value("${resilience.retry.max-attempts:3}") int maxAttempts,
            @Value("${resilience.retry.wait-duration-ms:500}") long waitDurationMs
    ) {
        var retryConfig = RetryConfig.custom()
                .maxAttempts(Math.max(1, maxAttempts))
                .waitDuration(Duration.ofMillis(Math.max(100, waitDurationMs)))
                .ignoreExceptions(DownstreamHttpException.class, IllegalArgumentException.class)
                .build();

        return RetryRegistry.of(retryConfig);
    }
}
