package com.rumal.product_service.repo;

import com.rumal.product_service.entity.Product;
import com.rumal.product_service.entity.ProductType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = {"categories", "variations", "images"})
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdWithDetails(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"categories", "variations", "images"})
    @Query("select p from Product p where p.slug = :slug")
    Optional<Product> findBySlugWithDetails(@Param("slug") String slug);

    List<Product> findByParentProductIdAndDeletedFalseAndActiveTrue(UUID parentProductId);
    boolean existsByParentProductIdAndDeletedFalseAndActiveTrueAndProductType(UUID parentProductId, ProductType productType);
    boolean existsByParentProductIdAndDeletedFalseAndActiveTrueAndVariationSignatureAndIdNot(UUID parentProductId, String variationSignature, UUID excludeId);
    Optional<Product> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);
    boolean existsByCategories_IdAndDeletedFalseAndActiveTrue(UUID categoryId);

    @Query("""
            select distinct p.parentProductId
            from Product p
            where p.parentProductId is not null
              and p.deleted = false
              and p.active = true
              and p.productType = com.rumal.product_service.entity.ProductType.VARIATION
            """)
    Set<UUID> findParentIdsWithActiveVariationChildren();

    @Modifying
    @Query("update Product p set p.approvalStatus = com.rumal.product_service.entity.ApprovalStatus.APPROVED where p.approvalStatus = com.rumal.product_service.entity.ApprovalStatus.DRAFT")
    int approveAllDraft();

    @Modifying
    @Query("update Product p set p.active = false where p.vendorId = :vendorId and p.active = true")
    int deactivateAllByVendorId(@Param("vendorId") UUID vendorId);

    @Modifying
    @Query("update Product p set p.viewCount = p.viewCount + 1 where p.id = :productId")
    int incrementViewCount(@Param("productId") UUID productId);

    @Query("""
            select distinct ps.product.id
            from ProductSpecification ps
            where ps.attributeKey = :key and ps.attributeValue = :value
            """)
    Set<UUID> findProductIdsBySpecification(@Param("key") String key, @Param("value") String value);

    @Query("""
            select distinct ps.product.id
            from ProductSpecification ps
            where ps.attributeKey = :key and ps.attributeValue = :value
              and ps.product.id in :productIds
            """)
    Set<UUID> findProductIdsBySpecificationAndProductIdIn(
            @Param("key") String key,
            @Param("value") String value,
            @Param("productIds") Collection<UUID> productIds
    );

    List<Product> findByIdInAndDeletedFalseAndActiveTrue(Collection<UUID> ids);

    @Query(value = "SELECT MAX(CAST(SUBSTRING(p.slug, LENGTH(:base) + 2) AS integer)) " +
            "FROM Product p WHERE p.slug LIKE CONCAT(:base, '-%') " +
            "AND p.slug ~ CONCAT(:base, '-[0-9]+$')",
            nativeQuery = true)
    Integer findMaxSlugSuffix(@Param("base") String base);

    @Query(value = "SELECT MAX(CAST(SUBSTRING(p.slug, LENGTH(:base) + 2) AS integer)) " +
            "FROM Product p WHERE p.slug LIKE CONCAT(:base, '-%') " +
            "AND p.slug ~ CONCAT(:base, '-[0-9]+$') AND p.id <> :excludeId",
            nativeQuery = true)
    Integer findMaxSlugSuffixExcluding(@Param("base") String base, @Param("excludeId") UUID excludeId);

    // --- Analytics queries ---

    long countByDeletedFalseAndActiveTrue();

    long countByDeletedFalseAndApprovalStatus(com.rumal.product_service.entity.ApprovalStatus approvalStatus);

    @Query("SELECT COALESCE(SUM(p.viewCount), 0) FROM Product p WHERE p.deleted = false")
    long sumTotalViews();

    @Query("SELECT COALESCE(SUM(p.soldCount), 0) FROM Product p WHERE p.deleted = false")
    long sumTotalSold();

    @Query("SELECT p.id, p.name, p.vendorId, p.viewCount FROM Product p WHERE p.deleted = false AND p.active = true ORDER BY p.viewCount DESC")
    List<Object[]> findTopByViews(Pageable pageable);

    @Query("SELECT p.id, p.name, p.vendorId, p.soldCount FROM Product p WHERE p.deleted = false AND p.active = true ORDER BY p.soldCount DESC")
    List<Object[]> findTopBySold(Pageable pageable);

    @Query("SELECT p.approvalStatus, COUNT(p) FROM Product p WHERE p.deleted = false GROUP BY p.approvalStatus")
    List<Object[]> countByApprovalStatusGrouped();

    // Vendor-specific
    @Query("SELECT COUNT(p) FROM Product p WHERE p.vendorId = :vendorId AND p.deleted = false")
    long countByVendorIdAndDeletedFalse(@Param("vendorId") UUID vendorId);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.vendorId = :vendorId AND p.deleted = false AND p.active = true")
    long countByVendorIdAndDeletedFalseAndActiveTrue(@Param("vendorId") UUID vendorId);

    @Query("SELECT COALESCE(SUM(p.viewCount), 0) FROM Product p WHERE p.vendorId = :vendorId AND p.deleted = false")
    long sumViewsByVendorId(@Param("vendorId") UUID vendorId);

    @Query("SELECT COALESCE(SUM(p.soldCount), 0) FROM Product p WHERE p.vendorId = :vendorId AND p.deleted = false")
    long sumSoldByVendorId(@Param("vendorId") UUID vendorId);
}
