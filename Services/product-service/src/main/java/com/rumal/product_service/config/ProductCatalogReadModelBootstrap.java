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

    @Override
    public void run(ApplicationArguments args) {
        if (!rebuildOnStartup) {
            return;
        }

        long productCount = productRepository.count();
        long readCount = productCatalogReadRepository.count();
        if (productCount == readCount) {
            return;
        }

        log.info(
                "Rebuilding product catalog read model on startup (products={}, readRows={})",
                productCount,
                readCount
        );
        productCatalogReadModelProjector.rebuildAll();
    }
}
