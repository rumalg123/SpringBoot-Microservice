package com.rumal.customer_service.config;

import com.rumal.customer_service.entity.*;
import com.rumal.customer_service.repo.CommunicationPreferencesRepository;
import com.rumal.customer_service.repo.CustomerAddressRepository;
import com.rumal.customer_service.repo.CustomerRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SampleCustomerDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleCustomerDataSeeder.class);

    public static final UUID CUSTOMER_ALICE_ID = UUID.fromString("aaaa1111-1111-1111-1111-111111111111");
    public static final UUID CUSTOMER_BOB_ID = UUID.fromString("aaaa2222-2222-2222-2222-222222222222");
    public static final UUID CUSTOMER_CAROL_ID = UUID.fromString("aaaa3333-3333-3333-3333-333333333333");
    public static final UUID CUSTOMER_DAVE_ID = UUID.fromString("aaaa4444-4444-4444-4444-444444444444");

    public static final UUID ALICE_HOME_ADDR_ID = UUID.fromString("bbbb1111-1111-1111-1111-111111111111");
    public static final UUID ALICE_OFFICE_ADDR_ID = UUID.fromString("bbbb1111-2222-2222-2222-222222222222");
    public static final UUID BOB_HOME_ADDR_ID = UUID.fromString("bbbb2222-1111-1111-1111-111111111111");
    public static final UUID CAROL_HOME_ADDR_ID = UUID.fromString("bbbb3333-1111-1111-1111-111111111111");
    public static final UUID DAVE_HOME_ADDR_ID = UUID.fromString("bbbb4444-1111-1111-1111-111111111111");

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final CommunicationPreferencesRepository commPrefsRepository;
    private final EntityManager entityManager;

    @Value("${sample.customer.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Skipping sample customer seed (sample.customer.seed.enabled=false)");
            return;
        }
        if (customerRepository.count() > 0) {
            log.info("Skipping sample customer seed because customers already exist.");
            return;
        }

        log.info("Seeding sample customers...");

        // Customer 1 — Alice (FEMALE, GOLD tier, active)
        Customer alice = persistCustomer(CUSTOMER_ALICE_ID, "Alice Johnson", "alice@example.com",
                "kc-customer-alice", "+1-555-1001", Gender.FEMALE,
                CustomerLoyaltyTier.GOLD, 2500, LocalDate.of(1992, 5, 15), true);

        persistAddress(ALICE_HOME_ADDR_ID, alice, "Home", "Alice Johnson", "+1-555-1001",
                "123 Maple Street", "Apt 4B", "New York", "NY", "10001", "US",
                true, true);

        persistAddress(ALICE_OFFICE_ADDR_ID, alice, "Office", "Alice Johnson", "+1-555-1002",
                "456 Broadway", "Suite 800", "New York", "NY", "10013", "US",
                false, false);

        persistCommPrefs(alice, true, false, true, true, true);

        // Customer 2 — Bob (MALE, SILVER tier, active)
        Customer bob = persistCustomer(CUSTOMER_BOB_ID, "Bob Smith", "bob@example.com",
                "kc-customer-bob", "+1-555-2001", Gender.MALE,
                CustomerLoyaltyTier.SILVER, 800, LocalDate.of(1988, 11, 3), true);

        persistAddress(BOB_HOME_ADDR_ID, bob, "Home", "Bob Smith", "+1-555-2001",
                "789 Oak Avenue", null, "Los Angeles", "CA", "90001", "US",
                true, true);

        persistCommPrefs(bob, false, false, false, true, false);

        // Customer 3 — Carol (OTHER gender, PLATINUM tier, active)
        Customer carol = persistCustomer(CUSTOMER_CAROL_ID, "Carol Rivera", "carol@example.com",
                "kc-customer-carol", "+1-555-3001", Gender.OTHER,
                CustomerLoyaltyTier.PLATINUM, 10000, LocalDate.of(1995, 8, 22), true);

        persistAddress(CAROL_HOME_ADDR_ID, carol, "Home", "Carol Rivera", "+1-555-3001",
                "321 Pine Road", "Unit 12", "Chicago", "IL", "60601", "US",
                true, true);

        persistCommPrefs(carol, true, true, true, true, true);

        // Customer 4 — Dave (PREFER_NOT_TO_SAY, BRONZE tier, inactive)
        Customer dave = persistCustomer(CUSTOMER_DAVE_ID, "Dave Park", "dave@example.com",
                "kc-customer-dave", "+1-555-4001", Gender.PREFER_NOT_TO_SAY,
                CustomerLoyaltyTier.BRONZE, 50, LocalDate.of(2000, 1, 10), false);

        persistAddress(DAVE_HOME_ADDR_ID, dave, "Home", "Dave Park", "+1-555-4001",
                "654 Elm Street", null, "Houston", "TX", "77001", "US",
                true, true);

        persistCommPrefs(dave, false, false, false, true, false);

        log.info("Sample customer seed completed: customers={}, addresses={}", customerRepository.count(), addressRepository.count());
    }

    private Customer persistCustomer(UUID id, String name, String email, String keycloakId,
                                     String phone, Gender gender, CustomerLoyaltyTier tier,
                                     int loyaltyPoints, LocalDate dob, boolean active) {
        Customer c = Customer.builder()
                .id(id)
                .name(name)
                .email(email)
                .keycloakId(keycloakId)
                .phone(phone)
                .gender(gender)
                .loyaltyTier(tier)
                .loyaltyPoints(loyaltyPoints)
                .dateOfBirth(dob)
                .active(active)
                .build();
        if (!active) {
            c.setDeactivatedAt(java.time.Instant.now());
        }
        entityManager.persist(c);
        return c;
    }

    private void persistAddress(UUID id, Customer customer, String label, String recipientName,
                                String phone, String line1, String line2, String city,
                                String state, String postalCode, String countryCode,
                                boolean defaultShipping, boolean defaultBilling) {
        entityManager.persist(CustomerAddress.builder()
                .id(id)
                .customer(customer)
                .label(label)
                .recipientName(recipientName)
                .phone(phone)
                .line1(line1)
                .line2(line2)
                .city(city)
                .state(state)
                .postalCode(postalCode)
                .countryCode(countryCode)
                .defaultShipping(defaultShipping)
                .defaultBilling(defaultBilling)
                .deleted(false)
                .build());
    }

    private void persistCommPrefs(Customer customer, boolean emailMarketing, boolean smsMarketing,
                                  boolean pushNotifications, boolean orderUpdates, boolean promotionalAlerts) {
        entityManager.persist(CommunicationPreferences.builder()
                .customer(customer)
                .emailMarketing(emailMarketing)
                .smsMarketing(smsMarketing)
                .pushNotifications(pushNotifications)
                .orderUpdates(orderUpdates)
                .promotionalAlerts(promotionalAlerts)
                .build());
    }
}
