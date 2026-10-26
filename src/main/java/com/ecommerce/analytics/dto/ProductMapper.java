package com.ecommerce.analytics.dto;

import com.ecommerce.analytics.dto.mapper.EntityMapper;
import com.ecommerce.analytics.entity.Product;
import org.springframework.stereotype.Component;

/**
 * Spring-managed {@link EntityMapper} for Product -> ProductResponse, for
 * call sites that need an injectable mapper (e.g. a generic "map this
 * Page<Product>" utility that takes an {@code EntityMapper<E, D>} rather
 * than a hardcoded method reference). It is a thin delegate to
 * {@link ProductResponse#fromEntity(Product)} - all the actual field
 * mapping lives in exactly one place (the DTO's own static factory), never
 * duplicated here.
 * <p>
 * {@link com.ecommerce.analytics.service.ProductService} does NOT use this
 * class - it calls {@code ProductResponse.fromEntity(product)} directly,
 * which is simpler for a service that already has the concrete Product/
 * ProductResponse types in hand and needs no injected dependency to do the
 * mapping. This bean exists for the polymorphic case described on
 * {@link EntityMapper}, not because the static factory needed replacing.
 */
@Component
public class ProductMapper implements EntityMapper<Product, ProductResponse> {

    @Override
    public ProductResponse toDto(Product entity) {
        return ProductResponse.fromEntity(entity);
    }
}
