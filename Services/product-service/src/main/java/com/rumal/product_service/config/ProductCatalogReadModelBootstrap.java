package com.rumal.product_service.config;

import com.rumal.product_service.repo.ProductCatalogReadRepository;
import com.rumal.product_service.repo.ProductRepository;
import com.rumal.product_service.service.ProductCatalogReadModelProjector;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class ProductCatalogReadModelBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogReadModelBootstrap.class);

    private final ProductRepository productRepository;
    private final ProductCatalogReadRepository productCatalogReadRepository;
    private final ProductCatalogReadModelProjector productCatalogReadModelProjector;

    @Value("${catalog.read-model.rebuild-on-startup:true}")
    private boolean rebuildOnStartup;

    @Value("${catalog.read-model.rebuild-delay-seconds:30}")
    private int rebuildDelaySeconds;

    @Override
    public void run(ApplicationArguments args) {
        if (!rebuildOnStartup) {
            return;
        }

        Thread.ofVirtual().name("catalog-read-model-rebuild").start(this::delayedRebuild);
    }

    private void delayedRebuild() {
        try {
            log.info("Catalog read model rebuild scheduled in {} seconds (waiting for dependent services)", rebuildDelaySeconds);
            Thread.sleep(Duration.ofSeconds(rebuildDelaySeconds));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Catalog read model rebuild interrupted during delay");
            return;
        }

        long productCount = productRepository.count();
        long readCount = productCatalogReadRepository.count();
        if (productCount == readCount) {
            log.info("Catalog read model is up to date (products={}, readRows={}), skipping rebuild", productCount, readCount);
            return;
        }

        log.info("Rebuilding product catalog read model (products={}, readRows={})", productCount, readCount);
        try {
            productCatalogReadModelProjector.rebuildAll();
        } catch (Exception ex) {
            log.error("Catalog read model rebuild failed: {}", ex.getMessage(), ex);
        }
    }
}
