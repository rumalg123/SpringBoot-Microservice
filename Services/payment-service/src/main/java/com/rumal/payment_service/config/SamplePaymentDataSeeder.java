package com.rumal.payment_service.config;

import com.rumal.payment_service.entity.VendorBankAccount;
import com.rumal.payment_service.repo.VendorBankAccountRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SamplePaymentDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SamplePaymentDataSeeder.class);

    private static final UUID NOVA_TECH_VENDOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID URBAN_STYLE_VENDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID HOME_CRAFT_VENDOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    public static final UUID NOVA_BANK_ID = UUID.fromString("dddd1111-1111-1111-1111-111111111111");
    public static final UUID URBAN_BANK_ID = UUID.fromString("dddd2222-2222-2222-2222-222222222222");
    public static final UUID HOME_CRAFT_BANK_ID = UUID.fromString("dddd3333-3333-3333-3333-333333333333");
    public static final UUID HOME_CRAFT_BANK_2_ID = UUID.fromString("dddd3333-4444-4444-4444-444444444444");

    private final VendorBankAccountRepository bankAccountRepository;
    private final EntityManager entityManager;

    @Value("${sample.payment.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Skipping sample payment seed (sample.payment.seed.enabled=false)");
            return;
        }
        if (bankAccountRepository.count() > 0) {
            log.info("Skipping sample payment seed because bank accounts already exist.");
            return;
        }

        log.info("Seeding sample vendor bank accounts...");

        // Nova Tech — primary active account
        persistBankAccount(NOVA_BANK_ID, NOVA_TECH_VENDOR_ID,
                "Silicon Valley Bank", "San Jose Main", "SVB-001",
                "1234567890", "Nova Tech Store Inc", "SVBKUS6S",
                true, true);

        // Urban Style — primary active account
        persistBankAccount(URBAN_BANK_ID, URBAN_STYLE_VENDOR_ID,
                "Chase Bank", "Manhattan Branch", "CHASE-NYC-42",
                "9876543210", "Urban Style House LLC", "CHASUS33",
                true, true);

        // Home Craft — primary active account
        persistBankAccount(HOME_CRAFT_BANK_ID, HOME_CRAFT_VENDOR_ID,
                "US Bank", "Portland Downtown", "USB-PDX-01",
                "5551234567", "Home Craft Market Co", "USBKUS44",
                true, true);

        // Home Craft — secondary inactive account (demonstrates non-primary + inactive)
        persistBankAccount(HOME_CRAFT_BANK_2_ID, HOME_CRAFT_VENDOR_ID,
                "Wells Fargo", "Portland East", "WF-PDX-02",
                "5559876543", "Home Craft Market Co", "WFBIUS6S",
                false, false);

        log.info("Sample payment seed completed: bankAccounts={}", bankAccountRepository.count());
    }

    private void persistBankAccount(UUID id, UUID vendorId, String bankName, String branchName,
                                    String branchCode, String accountNumber, String accountHolderName,
                                    String swiftCode, boolean primary, boolean active) {
        entityManager.merge(VendorBankAccount.builder()
                .id(id)
                .version(0L)
                .vendorId(vendorId)
                .bankName(bankName)
                .branchName(branchName)
                .branchCode(branchCode)
                .accountNumber(accountNumber)
                .accountHolderName(accountHolderName)
                .swiftCode(swiftCode)
                .primary(primary)
                .active(active)
                .build());
    }
}
