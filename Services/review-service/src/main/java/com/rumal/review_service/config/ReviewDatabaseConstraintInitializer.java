package com.rumal.review_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ReviewDatabaseConstraintInitializer {

    private static final Logger log = LoggerFactory.getLogger(ReviewDatabaseConstraintInitializer.class);

    @Bean
    public ApplicationRunner reviewConstraintRunner(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("""
                        CREATE UNIQUE INDEX IF NOT EXISTS ux_reviews_customer_product_active
                        ON reviews (customer_id, product_id)
                        WHERE deleted = false
                        """);
            } catch (RuntimeException ex) {
                log.warn("Failed to ensure review uniqueness index", ex);
            }
        };
    }
}
