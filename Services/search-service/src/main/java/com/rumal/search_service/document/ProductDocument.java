package com.rumal.search_service.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Document(indexName = "#{@environment.getProperty('search.index.name', 'products')}")
@Setting(settingPath = "elasticsearch/settings.json")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String slug;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "product_analyzer"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword),
                    @InnerField(suffix = "autocomplete", type = FieldType.Text, analyzer = "autocomplete_analyzer", searchAnalyzer = "standard")
            }
    )
    private String name;

    @Field(type = FieldType.Text, analyzer = "product_analyzer")
    private String shortDescription;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "product_analyzer"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private String brandName;

    @Field(type = FieldType.Keyword)
    private String sku;

    @Field(type = FieldType.Keyword, index = false)
    private String mainImage;

    @Field(type = FieldType.Double)
    private BigDecimal regularPrice;

    @Field(type = FieldType.Double)
    private BigDecimal discountedPrice;

    @Field(type = FieldType.Double)
    private BigDecimal sellingPrice;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "product_analyzer"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private String mainCategory;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "product_analyzer"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private Set<String> subCategories;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "product_analyzer"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private Set<String> categories;

    @Field(type = FieldType.Keyword)
    private String vendorId;

    @Field(type = FieldType.Keyword)
    private String productType;

    @Field(type = FieldType.Long)
    private long viewCount;

    @Field(type = FieldType.Long)
    private long soldCount;

    @Field(type = FieldType.Boolean)
    private boolean active;

    @Field(type = FieldType.Nested)
    private List<SpecificationEntry> specifications;

    @Field(type = FieldType.Nested)
    private List<VariationEntry> variations;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant createdAt;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SpecificationEntry {
        @Field(type = FieldType.Keyword)
        private String key;
        @Field(type = FieldType.Keyword)
        private String value;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VariationEntry {
        @Field(type = FieldType.Keyword)
        private String name;
        @Field(type = FieldType.Keyword)
        private String value;
    }
}
