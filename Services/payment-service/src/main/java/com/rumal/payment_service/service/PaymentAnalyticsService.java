package com.rumal.payment_service.service;

import com.rumal.payment_service.dto.analytics.*;
import com.rumal.payment_service.entity.PaymentStatus;
import com.rumal.payment_service.repo.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class PaymentAnalyticsService {

    private final PaymentRepository paymentRepository;

    public PaymentPlatformSummary getPlatformSummary() {
        long total = paymentRepository.count();
        long successful = paymentRepository.countByStatus(PaymentStatus.SUCCESS);
        long failed = paymentRepository.countByStatus(PaymentStatus.FAILED);
        BigDecimal successAmount = paymentRepository.sumAmountByStatus(PaymentStatus.SUCCESS);
        // No separate refund amount on Payment entity - use chargebacks as proxy
        long chargebacks = paymentRepository.countByStatus(PaymentStatus.CHARGEBACKED);
        BigDecimal chargebackAmount = paymentRepository.sumAmountByStatus(PaymentStatus.CHARGEBACKED);
        BigDecimal avgAmount = paymentRepository.avgSuccessfulPaymentAmount();

        return new PaymentPlatformSummary(total, successful, failed, successAmount,
            chargebackAmount, chargebacks, avgAmount);
    }

    public List<PaymentMethodBreakdown> getMethodBreakdown() {
        return paymentRepository.getPaymentMethodBreakdown().stream()
            .map(r -> new PaymentMethodBreakdown(
                (String) r[0],
                ((Number) r[1]).longValue(),
                (BigDecimal) r[2]))
            .toList();
    }
}
