package com.ecommerce.analytics.service;

import com.ecommerce.analytics.dto.ProductRequest;
import com.ecommerce.analytics.dto.ProductResponse;
import com.ecommerce.analytics.entity.Category;
import com.ecommerce.analytics.entity.Product;
import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventEnvelopeFactory;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.product.ProductCreatedEvent;
import com.ecommerce.analytics.event.payload.product.ProductDeletedEvent;
import com.ecommerce.analytics.event.payload.product.ProductUpdatedEvent;
import com.ecommerce.analytics.event.producer.DomainEventPublisher;
import com.ecommerce.analytics.exception.CategoryNotFoundException;
import com.ecommerce.analytics.exception.ProductInUseException;
import com.ecommerce.analytics.exception.ProductNotFoundException;
import com.ecommerce.analytics.repository.CartItemRepository;
import com.ecommerce.analytics.repository.CategoryRepository;
import com.ecommerce.analytics.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CartItemRepository cartItemRepository;
    private final EventEnvelopeFactory eventEnvelopeFactory;
    private final DomainEventPublisher domainEventPublisher;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            CartItemRepository cartItemRepository,
            EventEnvelopeFactory eventEnvelopeFactory,
            DomainEventPublisher domainEventPublisher
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.cartItemRepository = cartItemRepository;
        this.eventEnvelopeFactory = eventEnvelopeFactory;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {

        Category category = categoryRepository.findById(
                request.getCategoryId()
        ).orElseThrow(() ->
                new CategoryNotFoundException(
                        "Category not found with id: " + request.getCategoryId()
                )
        );

        Product product = new Product(
                request.getName(),
                request.getPrice()
        );

        product.setCategory(category);

        Product saved = productRepository.save(product);

        if (eventEnvelopeFactory != null && domainEventPublisher != null) {

            ProductCreatedEvent payload = new ProductCreatedEvent(
                    saved.getId(),
                    saved.getName(),
                    saved.getPrice(),
                    saved.getCategory() != null
                            ? saved.getCategory().getId()
                            : null
            );

            EventEnvelope<ProductCreatedEvent> event =
                    eventEnvelopeFactory.create(
                            EventType.PRODUCT_CREATED,
                            "Product",
                            saved.getId(),
                            payload
                    );

            domainEventPublisher.publish(event);
        }

        return ProductResponse.fromEntity(saved);
    }

    @Transactional
    public ProductResponse updateProduct(
            Long id,
            ProductRequest request
    ) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ProductNotFoundException(
                                "Product not found with id: " + id
                        )
                );

        Category category = categoryRepository.findById(
                request.getCategoryId()
        ).orElseThrow(() ->
                new CategoryNotFoundException(
                        "Category not found with id: " + request.getCategoryId()
                )
        );

        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setCategory(category);

        Product saved = productRepository.save(product);

        if (eventEnvelopeFactory != null && domainEventPublisher != null) {

            ProductUpdatedEvent payload = new ProductUpdatedEvent(
                    saved.getId(),
                    saved.getName(),
                    saved.getPrice(),
                    saved.getCategory() != null
                            ? saved.getCategory().getId()
                            : null
            );

            EventEnvelope<ProductUpdatedEvent> event =
                    eventEnvelopeFactory.create(
                            EventType.PRODUCT_UPDATED,
                            "Product",
                            saved.getId(),
                            payload
                    );

            domainEventPublisher.publish(event);
        }

        return ProductResponse.fromEntity(saved);
    }

    @Transactional
    public void deleteProduct(Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ProductNotFoundException(
                                "Product not found with id: " + id
                        )
                );

        boolean usedInCart =
                cartItemRepository.existsByProductId(id);

        if (usedInCart) {
            throw new ProductInUseException(
                    "Product cannot be deleted because it is used in cart items"

            );
        }

        productRepository.delete(product);

        if (eventEnvelopeFactory != null && domainEventPublisher != null) {

            ProductDeletedEvent payload =
                    new ProductDeletedEvent(id);

            EventEnvelope<ProductDeletedEvent> event =
                    eventEnvelopeFactory.create(
                            EventType.PRODUCT_DELETED,
                            "Product",
                            id,
                            payload
                    );

            domainEventPublisher.publish(event);
        }
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ProductNotFoundException(
                                "Product not found with id: " + id
                        )
                );

        return ProductResponse.fromEntity(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {

        return productRepository.findAll()
                .stream()
                .map(ProductResponse::fromEntity)
                .toList();
    }

    /** Paginated variant used by the /api/v1/products list endpoint. */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(ProductResponse::fromEntity);
    }

    // mapToResponse(Product) was removed - ProductResponse.fromEntity(product)
    // now owns this mapping (see that method's doc for why it lives on the
    // DTO rather than here). Every call site above was updated to call it
    // directly; no behavior changed, this is a pure refactor.
}
