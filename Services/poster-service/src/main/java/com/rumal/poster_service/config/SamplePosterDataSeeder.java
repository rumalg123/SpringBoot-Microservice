package com.rumal.poster_service.config;

import com.rumal.poster_service.dto.UpsertPosterRequest;
import com.rumal.poster_service.entity.PosterLinkType;
import com.rumal.poster_service.entity.PosterPlacement;
import com.rumal.poster_service.entity.PosterSize;
import com.rumal.poster_service.repo.PosterRepository;
import com.rumal.poster_service.service.PosterService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class SamplePosterDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SamplePosterDataSeeder.class);

    private static final String SEED_IMAGE = "posters/2eb6c6f0-0dcc-4f8c-815e-e81bbb216d50.jpeg";

    private final PosterService posterService;
    private final PosterRepository posterRepository;

    @Value("${sample.poster.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Skipping sample poster seed (sample.poster.seed.enabled=false)");
            return;
        }

        if (posterRepository.count() > 0) {
            log.info("Skipping sample poster seed because posters already exist.");
            return;
        }

        log.info("Seeding sample posters for all placements...");

        // Home hero carousel (multiple slides)
        createPoster(
                "Home Hero Electronics Launch",
                PosterPlacement.HOME_HERO,
                PosterSize.HERO,
                0,
                PosterLinkType.CATEGORY,
                "electronics",
                "Next-Gen Electronics",
                "Shop audio, accessories and gadgets with fast delivery and weekly deals.",
                "Shop Electronics",
                "#0c1225"
        );
        createPoster(
                "Home Hero Fashion Weekend",
                PosterPlacement.HOME_HERO,
                PosterSize.HERO,
                1,
                PosterLinkType.CATEGORY,
                "fashion",
                "Weekend Fashion Picks",
                "Styles, sneakers and everyday essentials curated for your next order.",
                "Shop Fashion",
                "#1a1028"
        );
        createPoster(
                "Home Hero Smart Search",
                PosterPlacement.HOME_HERO,
                PosterSize.HERO,
                2,
                PosterLinkType.SEARCH,
                "q=wireless&mainCategory=electronics",
                "Wireless Essentials",
                "Browse matching products from the latest electronics deals and trending picks.",
                "Search Now",
                "#071828"
        );

        // Home strips / tiles / grid
        createPoster(
                "Home Top Strip Deals",
                PosterPlacement.HOME_TOP_STRIP,
                PosterSize.STRIP,
                0,
                PosterLinkType.SEARCH,
                "q=deal",
                "Flash Deals Today",
                "Limited-time discounts across categories. Auto-rotates when multiple posters exist.",
                "View Deals",
                "#081a2e"
        );
        createPoster(
                "Home Top Strip New Arrivals",
                PosterPlacement.HOME_TOP_STRIP,
                PosterSize.STRIP,
                1,
                PosterLinkType.SEARCH,
                "q=new",
                "New Arrivals",
                "Fresh picks added for testing top-strip rotation and CTA links.",
                "Browse New",
                "#140d29"
        );

        createPoster(
                "Home Mid Left Promo 1",
                PosterPlacement.HOME_MID_LEFT,
                PosterSize.SQUARE,
                0,
                PosterLinkType.PRODUCT,
                "echobuds-wireless-earbuds",
                "Audio Pick",
                "Compact earbuds with strong battery and clean sound profile.",
                "View Product",
                "#0d1528"
        );
        createPoster(
                "Home Mid Left Promo 2",
                PosterPlacement.HOME_MID_LEFT,
                PosterSize.SQUARE,
                1,
                PosterLinkType.PRODUCT,
                "aerosound-bluetooth-speaker",
                "Portable Sound",
                "A second tile slide to validate autoplay and swipe on tile placements.",
                "Open Deal",
                "#102032"
        );

        createPoster(
                "Home Mid Right Promo 1",
                PosterPlacement.HOME_MID_RIGHT,
                PosterSize.SQUARE,
                0,
                PosterLinkType.CATEGORY,
                "home-living",
                "Home & Living",
                "Kitchen and decor picks grouped under a clean category landing page.",
                "Explore",
                "#151128"
        );
        createPoster(
                "Home Mid Right Promo 2",
                PosterPlacement.HOME_MID_RIGHT,
                PosterSize.SQUARE,
                1,
                PosterLinkType.CATEGORY,
                "electronics",
                "Accessories Sale",
                "Another tile slide for rotation checks in the home promo area.",
                "See More",
                "#0d1b2c"
        );

        createPoster(
                "Home Bottom Grid Promo 1",
                PosterPlacement.HOME_BOTTOM_GRID,
                PosterSize.WIDE,
                0,
                PosterLinkType.SEARCH,
                "mainCategory=electronics",
                "Electronics Picks",
                "Grid placement seed for future home sections and campaign rows.",
                "Browse",
                "#0d1222"
        );
        createPoster(
                "Home Bottom Grid Promo 2",
                PosterPlacement.HOME_BOTTOM_GRID,
                PosterSize.WIDE,
                1,
                PosterLinkType.SEARCH,
                "mainCategory=fashion",
                "Fashion Picks",
                "Second grid poster to validate list ordering and rotation behavior.",
                "Shop",
                "#1b1024"
        );

        // Category page placements
        createPoster(
                "Category Top Electronics Banner",
                PosterPlacement.CATEGORY_TOP,
                PosterSize.STRIP,
                0,
                PosterLinkType.CATEGORY,
                "electronics",
                "Electronics Specials",
                "Top category banner seed for category pages with CTA and link.",
                "Open Category",
                "#0b1a2b"
        );
        createPoster(
                "Category Top Fashion Banner",
                PosterPlacement.CATEGORY_TOP,
                PosterSize.STRIP,
                1,
                PosterLinkType.CATEGORY,
                "fashion",
                "Fashion Highlights",
                "Second category-top slide to test strip carousel in category pages.",
                "View Style",
                "#210f2d"
        );

        createPoster(
                "Category Sidebar Promo 1",
                PosterPlacement.CATEGORY_SIDEBAR,
                PosterSize.TALL,
                0,
                PosterLinkType.SEARCH,
                "q=accessories&mainCategory=electronics",
                "Accessory Finds",
                "Tall sidebar poster used for category layout experiments.",
                "Search",
                "#101a2d"
        );
        createPoster(
                "Category Sidebar Promo 2",
                PosterPlacement.CATEGORY_SIDEBAR,
                PosterSize.TALL,
                1,
                PosterLinkType.NONE,
                null,
                "Sidebar Placeholder",
                "No-link poster entry to test display-only marketing creatives.",
                null,
                "#1a1528"
        );

        // Product detail placement
        createPoster(
                "Product Detail Side Cross Sell 1",
                PosterPlacement.PRODUCT_DETAIL_SIDE,
                PosterSize.CUSTOM,
                0,
                PosterLinkType.PRODUCT,
                "novacharge-65w-fast-charger",
                "Add a Charger",
                "Cross-sell seed for product detail side placements.",
                "View Charger",
                "#0d172a"
        );
        createPoster(
                "Product Detail Side Cross Sell 2",
                PosterPlacement.PRODUCT_DETAIL_SIDE,
                PosterSize.CUSTOM,
                1,
                PosterLinkType.URL,
                "/products?mainCategory=electronics",
                "More Tech Deals",
                "Second side promo slide to verify rotation outside home page too.",
                "Browse Tech",
                "#131028"
        );

        log.info("Sample poster seed completed: posters={}", posterRepository.count());
    }

    private void createPoster(
            String name,
            PosterPlacement placement,
            PosterSize size,
            int sortOrder,
            PosterLinkType linkType,
            String linkTarget,
            String title,
            String subtitle,
            String ctaLabel,
            String backgroundColor
    ) {
        posterService.create(new UpsertPosterRequest(
                name,
                slug(name),
                placement,
                size,
                SEED_IMAGE,
                SEED_IMAGE,
                null,       // tabletImage
                null,       // srcsetDesktop
                null,       // srcsetMobile
                null,       // srcsetTablet
                linkType,
                linkTarget,
                linkType == PosterLinkType.URL,
                title,
                subtitle,
                ctaLabel,
                backgroundColor,
                sortOrder,
                true,
                null,
                null,
                null,
                null
        ));
    }

    private String slug(String value) {
        return value == null
                ? null
                : value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
