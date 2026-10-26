package com.ecommerce.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Deliberately does NOT extend {@link BaseResponse}, despite the request to
 * put ProductRequest and ProductResponse under the same base type - two
 * reasons, both about what this class actually is, not a style preference:
 * <ol>
 *   <li>ProductRequest never carries an id. Create and update both go
 *   through this same DTO (see ProductController/ProductService); on
 *   create there is no id yet (Postgres assigns it), and on update the id
 *   comes from the URL path variable, never from the request body -
 *   accepting an "id" field on an inbound create/update payload and
 *   letting the client set it is exactly the kind of mass-assignment
 *   footgun DTOs exist to prevent. Extending a base class whose entire
 *   purpose is "carries an id" onto a class that must never carry one
 *   would be actively misleading, not just unused.</li>
 *   <li>{@code BaseResponse} is a response-side concept by name and by
 *   doc (see its class comment) - a request is not a response. If a
 *   shared "has an id" base type is ever genuinely needed on the request
 *   side too (e.g. a future bulk-update request that legitimately
 *   addresses existing rows by id), the right move is a same-named-shape
 *   sibling (e.g. {@code IdentifiedRequest}), not reusing a type whose
 *   name and docs say "response."</li>
 * </ol>
 * There is also no static {@code toEntity(...)} factory here to mirror
 * ProductResponse's {@code fromEntity(...)}, for a similar inspection-based
 * reason: turning a ProductRequest into a Product entity needs a
 * {@code Category} lookup (this class only carries {@code categoryId}),
 * which is a repository call - a side-effecting, failable operation, not a
 * pure function of the request's own fields. That resolution (and its
 * {@code CategoryNotFoundException} handling) stays in
 * {@code ProductService}, where the repository already lives, rather than
 * being smuggled into a DTO that is supposed to have no dependencies.
 */
@Schema(
        name = "ProductRequest",
        description = "Request payload used to create or update a product"
)
public class ProductRequest {

    @Schema(
            description = "Name of the product",
            example = "Wireless Headphones",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Product name is required")
    private String name;

    @Schema(
            description = "Price of the product",
            example = "4999.99",
            minimum = "0.01",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Product price is required")
    @Positive(message = "Product price must be greater than 0")
    private Double price;

    @Schema(
            description = "ID of the category associated with the product",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Category ID is required")
    private Long categoryId;

    public ProductRequest() {
    }

    public ProductRequest(
            String name,
            Double price,
            Long categoryId
    ) {
        this.name = name;
        this.price = price;
        this.categoryId = categoryId;
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
