package com.ecommerce.analytics.service;

import com.ecommerce.analytics.dto.ProductRequest;
import com.ecommerce.analytics.dto.ProductResponse;
import com.ecommerce.analytics.entity.Category;
import com.ecommerce.analytics.entity.Product;
import com.ecommerce.analytics.exception.CategoryNotFoundException;
import com.ecommerce.analytics.exception.ProductInUseException;
import com.ecommerce.analytics.exception.ProductNotFoundException;
import com.ecommerce.analytics.repository.CartItemRepository;
import com.ecommerce.analytics.repository.CategoryRepository;
import com.ecommerce.analytics.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private Category category;

    @Mock
    private Product product;

    @InjectMocks
    private ProductService productService;


    @Test
    void createProduct_shouldCreateProductSuccessfully() {

        ProductRequest request =
                new ProductRequest(
                        "Laptop",
                        1099.99,
                        1L
                );

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category));

        when(category.getId())
                .thenReturn(1L);

        Product savedProduct = mock(Product.class);

        when(savedProduct.getId())
                .thenReturn(1L);

        when(savedProduct.getName())
                .thenReturn("Laptop");

        when(savedProduct.getPrice())
                .thenReturn(1099.99);

        when(savedProduct.getCategory())
                .thenReturn(category);

        when(productRepository.save(any(Product.class)))
                .thenReturn(savedProduct);

        ProductResponse response =
                productService.createProduct(request);

        assertNotNull(response);

        assertEquals(1L, response.getId());
        assertEquals("Laptop", response.getName());
        assertEquals(1099.99, response.getPrice());
        assertEquals(1L, response.getCategoryId());

        verify(categoryRepository)
                .findById(1L);

        verify(productRepository)
                .save(any(Product.class));

        verify(category)
                .getId();
    }


    @Test
    void createProduct_shouldThrowCategoryNotFoundException() {

        ProductRequest request =
                new ProductRequest(
                        "Laptop",
                        1099.99,
                        999L
                );

        when(categoryRepository.findById(999L))
                .thenReturn(Optional.empty());

        CategoryNotFoundException exception =
                assertThrows(
                        CategoryNotFoundException.class,
                        () -> productService.createProduct(request)
                );

        assertEquals(
                "Category not found with id: 999",
                exception.getMessage()
        );

        verify(categoryRepository)
                .findById(999L);

        verify(productRepository, never())
                .save(any(Product.class));
    }


    @Test
    void getProductById_shouldReturnProductSuccessfully() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(product.getId())
                .thenReturn(1L);

        when(product.getName())
                .thenReturn("Laptop");

        when(product.getPrice())
                .thenReturn(1099.99);

        when(product.getCategory())
                .thenReturn(category);

        when(category.getId())
                .thenReturn(1L);

        ProductResponse response =
                productService.getProductById(1L);

        assertNotNull(response);

        assertEquals(1L, response.getId());
        assertEquals("Laptop", response.getName());
        assertEquals(1099.99, response.getPrice());
        assertEquals(1L, response.getCategoryId());

        verify(productRepository)
                .findById(1L);
    }


    @Test
    void getProductById_shouldReturnNullCategoryIdWhenCategoryIsNull() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(product.getId())
                .thenReturn(1L);

        when(product.getName())
                .thenReturn("Laptop");

        when(product.getPrice())
                .thenReturn(1099.99);

        when(product.getCategory())
                .thenReturn(null);

        ProductResponse response =
                productService.getProductById(1L);

        assertNotNull(response);

        assertEquals(1L, response.getId());
        assertEquals("Laptop", response.getName());
        assertEquals(1099.99, response.getPrice());
        assertNull(response.getCategoryId());

        verify(productRepository)
                .findById(1L);

        verify(product, times(1))
                .getCategory();
    }


    @Test
    void getProductById_shouldThrowProductNotFoundException() {

        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());

        ProductNotFoundException exception =
                assertThrows(
                        ProductNotFoundException.class,
                        () -> productService.getProductById(999L)
                );

        assertEquals(
                "Product not found with id: 999",
                exception.getMessage()
        );

        verify(productRepository)
                .findById(999L);
    }


    @Test
    void getAllProducts_shouldReturnAllProducts() {

        Product product1 = mock(Product.class);
        Product product2 = mock(Product.class);

        Category category1 = mock(Category.class);
        Category category2 = mock(Category.class);

        when(product1.getId())
                .thenReturn(1L);

        when(product1.getName())
                .thenReturn("Laptop");

        when(product1.getPrice())
                .thenReturn(1099.99);

        when(product1.getCategory())
                .thenReturn(category1);

        when(category1.getId())
                .thenReturn(1L);


        when(product2.getId())
                .thenReturn(2L);

        when(product2.getName())
                .thenReturn("Mouse");

        when(product2.getPrice())
                .thenReturn(29.99);

        when(product2.getCategory())
                .thenReturn(category2);

        when(category2.getId())
                .thenReturn(1L);


        when(productRepository.findAll())
                .thenReturn(List.of(product1, product2));

        List<ProductResponse> responses =
                productService.getAllProducts();

        assertNotNull(responses);

        assertEquals(2, responses.size());

        assertEquals(1L, responses.get(0).getId());
        assertEquals("Laptop", responses.get(0).getName());
        assertEquals(1099.99, responses.get(0).getPrice());
        assertEquals(1L, responses.get(0).getCategoryId());

        assertEquals(2L, responses.get(1).getId());
        assertEquals("Mouse", responses.get(1).getName());
        assertEquals(29.99, responses.get(1).getPrice());
        assertEquals(1L, responses.get(1).getCategoryId());

        verify(productRepository)
                .findAll();
    }


    @Test
    void getAllProducts_shouldReturnEmptyListWhenNoProductsExist() {

        when(productRepository.findAll())
                .thenReturn(List.of());

        List<ProductResponse> responses =
                productService.getAllProducts();

        assertNotNull(responses);
        assertTrue(responses.isEmpty());

        verify(productRepository)
                .findAll();
    }


    @Test
    void getAllProducts_shouldHandleProductWithoutCategory() {

        Product productWithoutCategory =
                mock(Product.class);

        when(productWithoutCategory.getId())
                .thenReturn(1L);

        when(productWithoutCategory.getName())
                .thenReturn("Laptop");

        when(productWithoutCategory.getPrice())
                .thenReturn(1099.99);

        when(productWithoutCategory.getCategory())
                .thenReturn(null);

        when(productRepository.findAll())
                .thenReturn(List.of(productWithoutCategory));

        List<ProductResponse> responses =
                productService.getAllProducts();

        assertNotNull(responses);

        assertEquals(1, responses.size());

        assertEquals(
                1L,
                responses.get(0).getId()
        );

        assertEquals(
                "Laptop",
                responses.get(0).getName()
        );

        assertEquals(
                1099.99,
                responses.get(0).getPrice()
        );

        assertNull(
                responses.get(0).getCategoryId()
        );

        verify(productRepository)
                .findAll();
    }


    @Test
    void updateProduct_shouldUpdateProductSuccessfully() {

        ProductRequest request =
                new ProductRequest(
                        "Gaming Laptop",
                        1499.99,
                        2L
                );

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(categoryRepository.findById(2L))
                .thenReturn(Optional.of(category));

        when(category.getId())
                .thenReturn(2L);

        when(product.getId())
                .thenReturn(1L);

        when(product.getName())
                .thenReturn("Gaming Laptop");

        when(product.getPrice())
                .thenReturn(1499.99);

        when(product.getCategory())
                .thenReturn(category);

        when(productRepository.save(product))
                .thenReturn(product);

        ProductResponse response =
                productService.updateProduct(1L, request);

        assertNotNull(response);

        assertEquals(1L, response.getId());
        assertEquals(
                "Gaming Laptop",
                response.getName()
        );
        assertEquals(
                1499.99,
                response.getPrice()
        );
        assertEquals(
                2L,
                response.getCategoryId()
        );

        verify(productRepository)
                .findById(1L);

        verify(categoryRepository)
                .findById(2L);

        verify(product)
                .setName("Gaming Laptop");

        verify(product)
                .setPrice(1499.99);

        verify(product)
                .setCategory(category);

        verify(productRepository)
                .save(product);
    }


    @Test
    void updateProduct_shouldThrowProductNotFoundException() {

        ProductRequest request =
                new ProductRequest(
                        "Gaming Laptop",
                        1499.99,
                        2L
                );

        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());

        ProductNotFoundException exception =
                assertThrows(
                        ProductNotFoundException.class,
                        () -> productService.updateProduct(999L, request)
                );

        assertEquals(
                "Product not found with id: 999",
                exception.getMessage()
        );

        verify(productRepository)
                .findById(999L);

        verify(categoryRepository, never())
                .findById(anyLong());

        verify(productRepository, never())
                .save(any(Product.class));
    }


    @Test
    void updateProduct_shouldThrowCategoryNotFoundException() {

        ProductRequest request =
                new ProductRequest(
                        "Gaming Laptop",
                        1499.99,
                        999L
                );

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(categoryRepository.findById(999L))
                .thenReturn(Optional.empty());

        CategoryNotFoundException exception =
                assertThrows(
                        CategoryNotFoundException.class,
                        () -> productService.updateProduct(1L, request)
                );

        assertEquals(
                "Category not found with id: 999",
                exception.getMessage()
        );

        verify(productRepository)
                .findById(1L);

        verify(categoryRepository)
                .findById(999L);

        verify(product, never())
                .setName(anyString());

        verify(product, never())
                .setPrice(anyDouble());

        verify(product, never())
                .setCategory(any(Category.class));

        verify(productRepository, never())
                .save(any(Product.class));
    }


    @Test
    void deleteProduct_shouldDeleteProductSuccessfully() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(cartItemRepository.existsByProductId(1L))
                .thenReturn(false);

        productService.deleteProduct(1L);

        verify(productRepository)
                .findById(1L);

        verify(cartItemRepository)
                .existsByProductId(1L);

        verify(productRepository)
                .delete(product);
    }


    @Test
    void deleteProduct_shouldThrowProductNotFoundException() {

        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());

        ProductNotFoundException exception =
                assertThrows(
                        ProductNotFoundException.class,
                        () -> productService.deleteProduct(999L)
                );

        assertEquals(
                "Product not found with id: 999",
                exception.getMessage()
        );

        verify(productRepository)
                .findById(999L);

        verify(cartItemRepository, never())
                .existsByProductId(anyLong());

        verify(productRepository, never())
                .delete(any(Product.class));
    }


    @Test
    void deleteProduct_shouldThrowProductInUseException() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(cartItemRepository.existsByProductId(1L))
                .thenReturn(true);

        ProductInUseException exception =
                assertThrows(
                        ProductInUseException.class,
                        () -> productService.deleteProduct(1L)
                );

        assertEquals(
                "Product cannot be deleted because it is used in cart items",
                exception.getMessage()
        );

        verify(productRepository)
                .findById(1L);

        verify(cartItemRepository)
                .existsByProductId(1L);

        verify(productRepository, never())
                .delete(any(Product.class));
    }
}