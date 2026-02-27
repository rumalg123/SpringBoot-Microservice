package com.rumal.payment_service.controller;

import com.rumal.payment_service.dto.CreateVendorBankAccountRequest;
import com.rumal.payment_service.dto.UpdateVendorBankAccountRequest;
import com.rumal.payment_service.dto.VendorBankAccountResponse;
import com.rumal.payment_service.entity.VendorBankAccount;
import com.rumal.payment_service.exception.ResourceNotFoundException;
import com.rumal.payment_service.exception.UnauthorizedException;
import com.rumal.payment_service.repo.VendorBankAccountRepository;
import com.rumal.payment_service.security.InternalRequestVerifier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/payments/bank-accounts")
@RequiredArgsConstructor
public class AdminBankAccountController {

    private final VendorBankAccountRepository bankAccountRepository;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public Page<VendorBankAccountResponse> listAccounts(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestParam(required = false) UUID vendorId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        requireUserSub(userSub);
        if (vendorId != null) {
            return bankAccountRepository.findByVendorIdAndActiveTrue(vendorId, pageable).map(this::toResponse);
        }
        return bankAccountRepository.findAll(pageable).map(this::toResponse);
    }

    @PostMapping
    public VendorBankAccountResponse create(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @Valid @RequestBody CreateVendorBankAccountRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        requireUserSub(userSub);
        VendorBankAccount account = VendorBankAccount.builder()
                .vendorId(request.vendorId())
                .bankName(request.bankName())
                .branchName(request.branchName())
                .branchCode(request.branchCode())
                .accountNumber(request.accountNumber())
                .accountHolderName(request.accountHolderName())
                .swiftCode(request.swiftCode())
                .build();
        return toResponse(bankAccountRepository.save(account));
    }

    @PutMapping("/{id}")
    public VendorBankAccountResponse update(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVendorBankAccountRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        requireUserSub(userSub);
        VendorBankAccount account = bankAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found: " + id));
        if (request.bankName() != null) account.setBankName(request.bankName());
        if (request.branchName() != null) account.setBranchName(request.branchName());
        if (request.branchCode() != null) account.setBranchCode(request.branchCode());
        if (request.accountNumber() != null) account.setAccountNumber(request.accountNumber());
        if (request.accountHolderName() != null) account.setAccountHolderName(request.accountHolderName());
        if (request.swiftCode() != null) account.setSwiftCode(request.swiftCode());
        return toResponse(bankAccountRepository.save(account));
    }

    @DeleteMapping("/{id}")
    public VendorBankAccountResponse deactivate(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        requireUserSub(userSub);
        VendorBankAccount account = bankAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found: " + id));
        account.setActive(false);
        return toResponse(bankAccountRepository.save(account));
    }

    @PostMapping("/{id}/set-primary")
    @Transactional
    public VendorBankAccountResponse setPrimary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        requireUserSub(userSub);
        VendorBankAccount account = bankAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found: " + id));

        // Lock ALL active accounts for this vendor to prevent concurrent setPrimary
        List<VendorBankAccount> allAccounts = bankAccountRepository
                .findByVendorIdAndActiveTrueForUpdate(account.getVendorId());
        for (VendorBankAccount a : allAccounts) {
            a.setPrimary(a.getId().equals(id));
        }
        bankAccountRepository.saveAll(allAccounts);

        // Re-read to return fresh state
        account = bankAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found: " + id));
        return toResponse(account);
    }

    private String requireUserSub(String userSub) {
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return userSub.trim();
    }

    private VendorBankAccountResponse toResponse(VendorBankAccount account) {
        return new VendorBankAccountResponse(
                account.getId(),
                account.getVendorId(),
                account.getBankName(),
                account.getBranchName(),
                account.getBranchCode(),
                account.getAccountNumber(),
                account.getAccountHolderName(),
                account.getSwiftCode(),
                account.isPrimary(),
                account.isActive(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
