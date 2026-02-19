package com.rumal.product_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariationAttribute {

    @Column(name = "attribute_name", nullable = false, length = 60)
    private String name;

    @Column(name = "attribute_value", nullable = false, length = 100)
    private String value;
}

