package com.rumal.inventory_service.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryDatabaseConstraintInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureConstraint(
                "stock_items",
                "chk_stock_items_consistent",
                "check (quantity_on_hand >= 0 and quantity_reserved >= 0 and low_stock_threshold >= 0 and quantity_available = (quantity_on_hand - quantity_reserved) and (backorderable or quantity_available >= 0))"
        );
        ensureConstraint(
                "stock_reservations",
                "chk_stock_reservations_positive_quantity",
                "check (quantity_reserved > 0)"
        );
        ensureConstraint(
                "warehouses",
                "chk_warehouses_type_vendor_scope",
                "check (((warehouse_type = 'VENDOR_OWNED' and vendor_id is not null) or (warehouse_type = 'PLATFORM_MANAGED' and vendor_id is null)))"
        );
    }

    private void ensureConstraint(String tableName, String constraintName, String definition) {
        Integer existing = jdbcTemplate.queryForObject(
                "select count(*) from pg_constraint where conname = ? and conrelid = ?::regclass",
                Integer.class,
                constraintName,
                tableName
        );
        if (existing != null && existing > 0) {
            return;
        }

        jdbcTemplate.execute("alter table " + tableName + " add constraint " + constraintName + " " + definition);
        log.info("Ensured database constraint {} on {}", constraintName, tableName);
    }
}
