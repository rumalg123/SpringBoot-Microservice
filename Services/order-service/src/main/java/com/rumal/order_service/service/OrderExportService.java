package com.rumal.order_service.service;

import com.rumal.order_service.dto.OrderExportJobRequest;
import com.rumal.order_service.dto.OrderExportJobResponse;
import com.rumal.order_service.entity.OrderExportJob;
import com.rumal.order_service.entity.OrderExportJobStatus;
import com.rumal.order_service.entity.OrderStatus;
import com.rumal.order_service.exception.ResourceNotFoundException;
import com.rumal.order_service.exception.ValidationException;
import com.rumal.order_service.repo.OrderExportJobRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderExportService {

    private static final Logger log = LoggerFactory.getLogger(OrderExportService.class);
    private static final String EXPORT_FORMAT_CSV = "csv";
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 1000;

    private final OrderExportJobRepository orderExportJobRepository;
    private final OrderService orderService;
    private final OrderExportStorageService orderExportStorageService;

    @Value("${order.export.max-range:PT8760H}")
    private Duration maxExportRange;

    @Value("${order.export.processing-lease:PT10M}")
    private Duration processingLease;

    @Value("${order.export.retention:PT24H}")
    private Duration exportRetention;

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public OrderExportJobResponse createJob(OrderExportJobRequest request) {
        Instant now = Instant.now();
        String format = normalizeFormat(request.format());
        Instant createdBefore = request.createdBefore() != null ? request.createdBefore() : now;
        Instant createdAfter = request.createdAfter() != null ? request.createdAfter() : createdBefore.minus(maxExportRange);
        validateDateRange(createdAfter, createdBefore);
        OrderStatus filterStatus = parseOrderStatus(request.status());
        UUID vendorId = request.vendorId();
        String customerEmail = normalizeNullable(request.customerEmail());
        String requestedBy = normalizeNullable(request.requestedBy());
        String requestedRoles = normalizeNullable(request.requestedRoles());

        OrderExportJob saved = orderExportJobRepository.save(OrderExportJob.builder()
                .status(OrderExportJobStatus.PENDING)
                .format(format)
                .filterStatus(filterStatus)
                .customerEmail(customerEmail)
                .vendorId(vendorId)
                .requestedBy(requestedBy)
                .requestedRoles(requestedRoles)
                .createdAfter(createdAfter)
                .createdBefore(createdBefore)
                .fileName(buildFileName(vendorId, now))
                .contentType("text/csv")
                .expiresAt(now.plus(exportRetention))
                .build());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderExportJobResponse getJob(UUID jobId) {
        OrderExportJob job = orderExportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Order export job not found: " + jobId));
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public OrderExportStorageService.StoredOrderExportFile download(UUID jobId) {
        OrderExportJob job = orderExportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Order export job not found: " + jobId));
        if (job.getStatus() != OrderExportJobStatus.COMPLETED || !StringUtils.hasText(job.getStorageKey())) {
            throw new ValidationException("Order export is not ready for download");
        }
        if (job.getExpiresAt() != null && job.getExpiresAt().isBefore(Instant.now())) {
            throw new ValidationException("Order export has expired");
        }
        return orderExportStorageService.load(job.getStorageKey(), job.getFileName(), job.getContentType());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public List<UUID> claimJobsForProcessing(Instant now, int limit) {
        List<OrderExportJob> jobs = orderExportJobRepository.findJobsReadyToClaim(now, limit);
        if (jobs.isEmpty()) {
            return List.of();
        }
        Instant leaseUntil = now.plus(processingLease);
        for (OrderExportJob job : jobs) {
            job.setStatus(OrderExportJobStatus.PROCESSING);
            job.setStartedAt(now);
            job.setProcessingLeaseUntil(leaseUntil);
            job.setFailureMessage(null);
            job.setCompletedAt(null);
        }
        orderExportJobRepository.saveAll(jobs);
        return jobs.stream().map(OrderExportJob::getId).toList();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void processJob(UUID jobId) {
        OrderExportJob job = orderExportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Order export job not found: " + jobId));
        if (job.getStatus() != OrderExportJobStatus.PROCESSING) {
            return;
        }

        try {
            OrderService.OrderCsvExportResult csvExportResult = orderService.generateOrdersCsv(
                    job.getFilterStatus(),
                    job.getCreatedAfter(),
                    job.getCreatedBefore(),
                    job.getVendorId(),
                    job.getCustomerEmail()
            );
            OrderExportStorageService.StoredOrderExportFile storedFile = orderExportStorageService.store(
                    job.getId(),
                    job.getFileName(),
                    job.getContentType(),
                    csvExportResult.content()
            );
            markCompleted(jobId, storedFile, csvExportResult.rowCount());
        } catch (Exception ex) {
            log.error("Failed processing order export job {}", jobId, ex);
            markFailed(jobId, ex);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public void expireReadyJobs(Instant now) {
        Set<OrderExportJobStatus> expirableStatuses = EnumSet.of(
                OrderExportJobStatus.COMPLETED,
                OrderExportJobStatus.FAILED
        );
        List<OrderExportJob> expiredJobs = orderExportJobRepository.findExpiredJobs(expirableStatuses, now);
        if (expiredJobs.isEmpty()) {
            return;
        }
        for (OrderExportJob job : expiredJobs) {
            orderExportStorageService.delete(job.getStorageKey());
            job.setStatus(OrderExportJobStatus.EXPIRED);
            job.setStorageKey(null);
            job.setProcessingLeaseUntil(null);
        }
        orderExportJobRepository.saveAll(expiredJobs);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public void markCompleted(
            UUID jobId,
            OrderExportStorageService.StoredOrderExportFile storedFile,
            int rowCount
    ) {
        OrderExportJob job = orderExportJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Order export job not found: " + jobId));
        job.setStatus(OrderExportJobStatus.COMPLETED);
        job.setStorageKey(storedFile.storageKey());
        job.setFileSizeBytes(storedFile.contentLength());
        job.setRowCount(rowCount);
        job.setCompletedAt(Instant.now());
        job.setFailureMessage(null);
        job.setProcessingLeaseUntil(null);
        job.setExpiresAt(Instant.now().plus(exportRetention));
        orderExportJobRepository.save(job);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public void markFailed(UUID jobId, Exception ex) {
        OrderExportJob job = orderExportJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Order export job not found: " + jobId));
        job.setStatus(OrderExportJobStatus.FAILED);
        job.setFailureMessage(truncateFailureMessage(ex));
        job.setCompletedAt(Instant.now());
        job.setProcessingLeaseUntil(null);
        job.setExpiresAt(Instant.now().plus(exportRetention));
        orderExportJobRepository.save(job);
    }

    private OrderExportJobResponse toResponse(OrderExportJob job) {
        return new OrderExportJobResponse(
                job.getId(),
                job.getStatus().name(),
                job.getFormat(),
                job.getFileName(),
                job.getContentType(),
                job.getCustomerEmail(),
                job.getFilterStatus() == null ? null : job.getFilterStatus().name(),
                job.getVendorId(),
                job.getRequestedBy(),
                job.getRowCount(),
                job.getFileSizeBytes(),
                job.getFailureMessage(),
                job.getCreatedAfter(),
                job.getCreatedBefore(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getExpiresAt()
        );
    }

    private String normalizeFormat(String format) {
        if (!StringUtils.hasText(format)) {
            return EXPORT_FORMAT_CSV;
        }
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        if (!EXPORT_FORMAT_CSV.equals(normalized)) {
            throw new ValidationException("Unsupported export format: " + format);
        }
        return normalized;
    }

    private OrderStatus parseOrderStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Unsupported order status: " + status);
        }
    }

    private void validateDateRange(Instant createdAfter, Instant createdBefore) {
        if (createdAfter != null && createdBefore != null && createdAfter.isAfter(createdBefore)) {
            throw new ValidationException("createdAfter must be before createdBefore");
        }
        if (createdAfter == null || createdBefore == null) {
            return;
        }
        Duration actualRange = Duration.between(createdAfter, createdBefore);
        if (actualRange.isNegative()) {
            throw new ValidationException("Invalid export date range");
        }
        if (actualRange.compareTo(maxExportRange) > 0) {
            long days = Math.max(1, maxExportRange.toDays());
            throw new ValidationException("Order export date range must not exceed " + days + " days");
        }
    }

    private String buildFileName(UUID vendorId, Instant now) {
        String datePart = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneOffset.UTC)
                .format(now);
        if (vendorId == null) {
            return "orders-export-" + datePart + ".csv";
        }
        return "vendor-orders-export-" + vendorId + "-" + datePart + ".csv";
    }

    private String truncateFailureMessage(Exception ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            message = ex.getClass().getSimpleName();
        }
        String normalized = message.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= MAX_FAILURE_MESSAGE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_FAILURE_MESSAGE_LENGTH);
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
