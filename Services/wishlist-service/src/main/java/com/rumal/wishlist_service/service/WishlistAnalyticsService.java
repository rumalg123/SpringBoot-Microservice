package com.rumal.wishlist_service.service;

import com.rumal.wishlist_service.dto.analytics.*;
import com.rumal.wishlist_service.repo.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class WishlistAnalyticsService {

    private final WishlistItemRepository wishlistItemRepository;

    public WishlistPlatformSummary getPlatformSummary() {
        long total = wishlistItemRepository.count();
        long uniqueCustomers = wishlistItemRepository.countDistinctCustomers();
        long uniqueProducts = wishlistItemRepository.countDistinctProducts();
        return new WishlistPlatformSummary(total, uniqueCustomers, uniqueProducts);
    }

    public List<MostWishedProduct> getMostWished(int limit) {
        return wishlistItemRepository.findMostWishedProducts(PageRequest.of(0, limit)).stream()
            .map(r -> new MostWishedProduct(
                (UUID) r[0], (String) r[1], ((Number) r[2]).longValue()))
            .toList();
    }
}
