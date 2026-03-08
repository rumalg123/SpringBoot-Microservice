package com.rumal.order_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_export_jobs", indexes = {
        @Index(name = "idx_order_export_jobs_status_created", columnList = "status, created_at"),
        @Index(name = "idx_order_export_jobs_vendor_status", columnList = "vendor_id, status"),
        @Index(name = "idx_order_export_jobs_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderExportJob {

    @Id
    @GeneratedValue
    private UUID id;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderExportJobStatus status = OrderExportJobStatus.PENDING;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String format = "csv";

    @Enumerated(EnumType.STRING)
    @Column(name = "filter_status", length = 32)
    private OrderStatus filterStatus;

    @Column(name = "customer_email", length = 320)
    private String customerEmail;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Column(name = "requested_by", length = 120)
    private String requestedBy;

    @Column(name = "requested_roles", length = 500)
    private String requestedRoles;

    @Column(name = "created_after")
    private Instant createdAfter;

    @Column(name = "created_before")
    private Instant createdBefore;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "processing_lease_until")
    private Instant processingLeaseUntil;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
