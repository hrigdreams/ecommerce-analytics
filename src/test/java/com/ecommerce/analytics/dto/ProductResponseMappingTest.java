package com.ecommerce.analytics.dto;

import com.ecommerce.analytics.entity.Category;
import com.ecommerce.analytics.entity.Product;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers the refactor itself: ProductResponse.fromEntity must behave
 * identically to the private mapToResponse(Product) it replaced in
 * ProductService (same fields, same null-category handling), and the new
 * BaseResponse/ProductMapper plumbing must actually work end to end.
 */
class ProductResponseMappingTest {

    @Test
    void fromEntity_shouldMapAllFieldsIncludingCategoryId() {
        Category category = new Category("Electronics");
        setId(category, 5L);

        Product product = new Product("Headphones", 199.99);
        product.setCategory(category);
        setId(product, 10L);

        ProductResponse response = ProductResponse.fromEntity(product);

        assertEquals(10L, response.getId());
        assertEquals("Headphones", response.getName());
        assertEquals(199.99, response.getPrice());
        assertEquals(5L, response.getCategoryId());
    }

    @Test
    void fromEntity_withNoCategory_shouldMapCategoryIdAsNull_notThrow() {
        Product product = new Product("Mystery Item", 1.0);
        setId(product, 11L);
        // category deliberately left null

        ProductResponse response = ProductResponse.fromEntity(product);

        assertNull(response.getCategoryId());
    }

    @Test
    void fromEntity_withNullEntity_shouldReturnNull() {
        assertNull(ProductResponse.fromEntity(null));
    }

    @Test
    void baseResponse_idAccessors_shouldWorkThroughTheSubclass() {
        ProductResponse response = new ProductResponse();
        response.setId(42L);

        assertEquals(42L, response.getId());
    }

    @Test
    void productMapper_shouldDelegateToTheSameStaticFactory() {
        Product product = new Product("Widget", 9.99);
        setId(product, 7L);

        ProductMapper mapper = new ProductMapper();
        ProductResponse viaMapper = mapper.toDto(product);
        ProductResponse viaStaticFactory = ProductResponse.fromEntity(product);

        assertEquals(viaStaticFactory.getId(), viaMapper.getId());
        assertEquals(viaStaticFactory.getName(), viaMapper.getName());
        assertEquals(viaStaticFactory.getPrice(), viaMapper.getPrice());
        assertEquals(viaStaticFactory.getCategoryId(), viaMapper.getCategoryId());
    }

    /** Neither Product nor Category exposes a public id setter (assigned by JPA) - set it via reflection for this pure-unit test. */
    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
