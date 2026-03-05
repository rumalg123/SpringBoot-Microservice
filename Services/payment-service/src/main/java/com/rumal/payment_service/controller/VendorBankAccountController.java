package com.rumal.payment_service.controller;

import com.rumal.payment_service.dto.CreateMyVendorBankAccountRequest;
import com.rumal.payment_service.dto.UpdateVendorBankAccountRequest;
import com.rumal.payment_service.dto.VendorBankAccountResponse;
import com.rumal.payment_service.entity.VendorBankAccount;
import com.rumal.payment_service.exception.ResourceNotFoundException;
import com.rumal.payment_service.exception.UnauthorizedException;
import com.rumal.payment_service.repo.VendorBankAccountRepository;
import com.rumal.payment_service.security.InternalRequestVerifier;
import com.rumal.payment_service.service.PaymentAccessScopeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.transaction.annotation.Transactional;
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
@RequestMapping("/payments/vendor/me/bank-accounts")
@RequiredArgsConstructor
public class VendorBankAccountController {

    private final VendorBankAccountRepository bankAccountRepository;
    private final InternalRequestVerifier internalRequestVerifier;
    private final PaymentAccessScopeService paymentAccessScopeService;

    @GetMapping
    public Page<VendorBankAccountResponse> listAccounts(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        var scope = paymentAccessScopeService.resolveScope(requireUserSub(userSub), userRoles, internalAuth);
        UUID resolvedVendorId = paymentAccessScopeService.resolveVendorIdForVendorFinanceRead(scope, vendorId);
        return bankAccountRepository.findByVendorId(resolvedVendorId, pageable).map(this::toResponse);
    }

    @PostMapping
    public VendorBankAccountResponse create(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @Valid @RequestBody CreateMyVendorBankAccountRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        var scope = paymentAccessScopeService.resolveScope(requireUserSub(userSub), userRoles, internalAuth);
        UUID resolvedVendorId = paymentAccessScopeService.resolveVendorIdForVendorFinanceManage(scope, vendorId);
        VendorBankAccount account = VendorBankAccount.builder()
                .vendorId(resolvedVendorId)
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
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVendorBankAccountRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        var scope = paymentAccessScopeService.resolveScope(requireUserSub(userSub), userRoles, internalAuth);
        UUID resolvedVendorId = paymentAccessScopeService.resolveVendorIdForVendorFinanceManage(scope, vendorId);
        VendorBankAccount account = findOwnedAccount(resolvedVendorId, id);
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
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        var scope = paymentAccessScopeService.resolveScope(requireUserSub(userSub), userRoles, internalAuth);
        UUID resolvedVendorId = paymentAccessScopeService.resolveVendorIdForVendorFinanceManage(scope, vendorId);
        VendorBankAccount account = findOwnedAccount(resolvedVendorId, id);
        account.setActive(false);
        return toResponse(bankAccountRepository.save(account));
    }

    @PostMapping("/{id}/set-primary")
    @Transactional
    public VendorBankAccountResponse setPrimary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        var scope = paymentAccessScopeService.resolveScope(requireUserSub(userSub), userRoles, internalAuth);
        UUID resolvedVendorId = paymentAccessScopeService.resolveVendorIdForVendorFinanceManage(scope, vendorId);
        VendorBankAccount account = findOwnedAccount(resolvedVendorId, id);

        List<VendorBankAccount> allAccounts = bankAccountRepository.findByVendorIdAndActiveTrueForUpdate(resolvedVendorId);
        for (VendorBankAccount a : allAccounts) {
            a.setPrimary(a.getId().equals(id));
        }
        bankAccountRepository.saveAll(allAccounts);

        account = bankAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found: " + id));
        return toResponse(account);
    }

    private VendorBankAccount findOwnedAccount(UUID vendorId, UUID accountId) {
        VendorBankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found: " + accountId));
        if (!vendorId.equals(account.getVendorId())) {
            throw new ResourceNotFoundException("Bank account not found: " + accountId);
        }
        return account;
    }

    private String requireUserSub(String userSub) {
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return userSub.trim();
    }

    private void verifyEmailVerified(String emailVerified) {
        if (emailVerified == null || !"true".equalsIgnoreCase(emailVerified.trim())) {
            throw new UnauthorizedException("Email is not verified");
        }
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
