package com.ecommerce.analytics.dto;

import com.ecommerce.analytics.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "ProductResponse",
        description = "Response containing product information"
)
public class ProductResponse extends BaseResponse {

    @Schema(
            description = "Name of the product",
            example = "Wireless Headphones"
    )
    private String name;

    @Schema(
            description = "Price of the product",
            example = "4999.99"
    )
    private Double price;

    @Schema(
            description = "ID of the category associated with the product",
            example = "1"
    )
    private Long categoryId;

    public ProductResponse() {
        super();
    }

    public ProductResponse(
            Long id,
            String name,
            Double price,
            Long categoryId
    ) {
        super(id);
        this.name = name;
        this.price = price;
        this.categoryId = categoryId;
    }

    /**
     * Replaces ProductService's old private mapToResponse(Product) method -
     * the DTO now owns its own entity->response mapping instead of the
     * service hand-setting each field. A static factory (not an instance
     * method/constructor call site spread across the service) because this
     * mapping needs no dependencies beyond the Product entity itself (no
     * repository lookup, no Spring bean) - it is a pure function of its
     * input, which is exactly what a static factory communicates.
     * <p>
     * Returns null for a null entity rather than throwing, matching the
     * previous mapToResponse's implicit behavior (it was only ever called
     * with a non-null, already-validated/already-fetched Product, so this
     * is defensive rather than a behavior this project currently exercises).
     */
    public static ProductResponse fromEntity(Product product) {
        if (product == null) {
            return null;
        }

        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setPrice(product.getPrice());
        response.setCategoryId(product.getCategory() != null ? product.getCategory().getId() : null);
        return response;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}
