package com.ecommerce.analytics.service;

import com.ecommerce.analytics.dto.ProductRequest;
import com.ecommerce.analytics.dto.ProductResponse;
import com.ecommerce.analytics.entity.Category;
import com.ecommerce.analytics.entity.Product;
import com.ecommerce.analytics.exception.CategoryNotFoundException;
import com.ecommerce.analytics.exception.ProductNotFoundException;
import com.ecommerce.analytics.repository.CategoryRepository;
import com.ecommerce.analytics.repository.ProductRepository;
import org.springframework.stereotype.Service;
import com.ecommerce.analytics.exception.ProductInUseException;
import com.ecommerce.analytics.repository.CartItemRepository;

import java.util.List;

@Service
public class ProductService {
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            CartItemRepository cartItemRepository
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.cartItemRepository = cartItemRepository;
    }

    public ProductResponse createProduct(ProductRequest request) {

        Category category = categoryRepository
                .findById(request.getCategoryId())
                .orElseThrow(() ->
                        new CategoryNotFoundException(
                                "Category not found with id: "
                                        + request.getCategoryId()
                        )
                );

        Product product = new Product(
                request.getName(),
                request.getPrice()
        );

        product.setCategory(category);

        Product savedProduct = productRepository.save(product);

        return new ProductResponse(
                savedProduct.getId(),
                savedProduct.getName(),
                savedProduct.getPrice(),
                savedProduct.getCategory().getId()
        );
    }

    public ProductResponse getProductById(Long id) {

        Product product = productRepository
                .findById(id)
                .orElseThrow(() ->
                        new ProductNotFoundException(
                                "Product not found with id: " + id
                        )
                );

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCategory() != null
                        ? product.getCategory().getId()
                        : null
        );
    }

    public List<ProductResponse> getAllProducts() {

        return productRepository.findAll()
                .stream()
                .map(product -> new ProductResponse(
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        product.getCategory() != null
                                ? product.getCategory().getId()
                                : null
                ))
                .toList();
    }

    public ProductResponse updateProduct(
            Long id,
            ProductRequest request
    ) {

        Product product = productRepository
                .findById(id)
                .orElseThrow(() ->
                        new ProductNotFoundException(
                                "Product not found with id: " + id
                        )
                );

        Category category = categoryRepository
                .findById(request.getCategoryId())
                .orElseThrow(() ->
                        new CategoryNotFoundException(
                                "Category not found with id: "
                                        + request.getCategoryId()
                        )
                );

        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setCategory(category);

        Product updatedProduct = productRepository.save(product);

        return new ProductResponse(
                updatedProduct.getId(),
                updatedProduct.getName(),
                updatedProduct.getPrice(),
                updatedProduct.getCategory().getId()
        );
    }

    public void deleteProduct(Long id) {

        Product product = productRepository
                .findById(id)
                .orElseThrow(() ->
                        new ProductNotFoundException(
                                "Product not found with id: " + id
                        )
                );

        if (cartItemRepository.existsByProductId(id)) {
            throw new ProductInUseException(
                    "Product cannot be deleted because it is used in cart items"
            );
        }

        productRepository.delete(product);
    }
}