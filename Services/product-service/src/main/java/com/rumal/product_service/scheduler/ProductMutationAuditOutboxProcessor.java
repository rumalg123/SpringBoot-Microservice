package com.rumal.product_service.scheduler;

import com.rumal.product_service.service.ProductServiceImpl;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductMutationAuditOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(ProductMutationAuditOutboxProcessor.class);

    private final ProductServiceImpl productService;

    @Value("${product.audit.outbox.batch-size:50}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${product.audit.outbox.poll-interval-ms:5000}",
            initialDelayString = "${product.audit.outbox.initial-delay-ms:10000}"
    )
    public void process() {
        try {
            productService.processMutationAuditOutboxBatch(batchSize);
        } catch (Exception ex) {
            log.error("Product mutation audit outbox batch failed", ex);
        }
    }
}
