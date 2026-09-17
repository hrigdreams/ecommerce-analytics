package com.ecommerce.analytics.service;

import com.ecommerce.analytics.dto.CategoryRequest;
import com.ecommerce.analytics.dto.CategoryResponse;
import com.ecommerce.analytics.entity.Category;
import com.ecommerce.analytics.exception.CategoryInUseException;
import com.ecommerce.analytics.exception.CategoryNotFoundException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private Category category;


    @InjectMocks
    private CategoryService categoryService;


    @Test
    void createCategory_shouldCreateSuccessfully() {

        CategoryRequest request =
                new CategoryRequest("Electronics");

        Category savedCategory = mock(Category.class);

        when(savedCategory.getId()).thenReturn(1L);
        when(savedCategory.getName()).thenReturn("Electronics");

        when(categoryRepository.save(any(Category.class)))
                .thenReturn(savedCategory);

        CategoryResponse response =
                categoryService.createCategory(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Electronics", response.getName());

        verify(categoryRepository).save(any(Category.class));
    }


    @Test
    void getAllCategories_shouldReturnAllCategories() {

        Category category1 = mock(Category.class);
        Category category2 = mock(Category.class);

        when(category1.getId()).thenReturn(1L);
        when(category1.getName()).thenReturn("Electronics");

        when(category2.getId()).thenReturn(2L);
        when(category2.getName()).thenReturn("Clothing");

        when(categoryRepository.findAll())
                .thenReturn(List.of(category1, category2));

        List<CategoryResponse> responses =
                categoryService.getAllCategories();

        assertNotNull(responses);
        assertEquals(2, responses.size());

        assertEquals(1L, responses.get(0).getId());
        assertEquals("Electronics", responses.get(0).getName());

        assertEquals(2L, responses.get(1).getId());
        assertEquals("Clothing", responses.get(1).getName());

        verify(categoryRepository).findAll();
    }


    @Test
    void getCategoryById_shouldReturnSuccessfully() {

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category));

        when(category.getId()).thenReturn(1L);
        when(category.getName()).thenReturn("Electronics");

        CategoryResponse response =
                categoryService.getCategoryById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Electronics", response.getName());

        verify(categoryRepository).findById(1L);
    }


    @Test
    void getCategoryById_shouldThrowCategoryNotFoundException() {

        when(categoryRepository.findById(999L))
                .thenReturn(Optional.empty());

        CategoryNotFoundException exception =
                assertThrows(
                        CategoryNotFoundException.class,
                        () -> categoryService.getCategoryById(999L)
                );

        assertEquals(
                "Category not found with id: 999",
                exception.getMessage()
        );

        verify(categoryRepository).findById(999L);
    }


    @Test
    void updateCategory_shouldUpdateSuccessfully() {

        CategoryRequest request =
                new CategoryRequest("Updated Electronics");

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category));

        when(category.getId()).thenReturn(1L);
        when(category.getName()).thenReturn("Updated Electronics");

        when(categoryRepository.save(category))
                .thenReturn(category);

        CategoryResponse response =
                categoryService.updateCategory(1L, request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Updated Electronics", response.getName());

        verify(categoryRepository).findById(1L);

        verify(category).setName("Updated Electronics");

        verify(categoryRepository).save(category);
    }


    @Test
    void updateCategory_shouldThrowCategoryNotFoundException() {

        CategoryRequest request =
                new CategoryRequest("Updated Electronics");

        when(categoryRepository.findById(999L))
                .thenReturn(Optional.empty());

        CategoryNotFoundException exception =
                assertThrows(
                        CategoryNotFoundException.class,
                        () -> categoryService.updateCategory(999L, request)
                );

        assertEquals(
                "Category not found with id: 999",
                exception.getMessage()
        );

        verify(categoryRepository).findById(999L);

        verify(category, never()).setName(anyString());

        verify(categoryRepository, never())
                .save(any(Category.class));
    }


    @Test
    void deleteCategory_shouldDeleteSuccessfully() {

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category));

        when(productRepository.existsByCategoryId(1L))
                .thenReturn(false);

        categoryService.deleteCategory(1L);

        verify(categoryRepository).findById(1L);

        verify(productRepository)
                .existsByCategoryId(1L);

        verify(categoryRepository)
                .delete(category);
    }


    @Test
    void deleteCategory_shouldThrowCategoryNotFoundException() {

        when(categoryRepository.findById(999L))
                .thenReturn(Optional.empty());

        CategoryNotFoundException exception =
                assertThrows(
                        CategoryNotFoundException.class,
                        () -> categoryService.deleteCategory(999L)
                );

        assertEquals(
                "Category not found with id: 999",
                exception.getMessage()
        );

        verify(categoryRepository).findById(999L);

        verify(productRepository, never())
                .existsByCategoryId(anyLong());

        verify(categoryRepository, never())
                .delete(any(Category.class));
    }


    @Test
    void deleteCategory_shouldThrowCategoryInUseException() {

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category));

        when(productRepository.existsByCategoryId(1L))
                .thenReturn(true);

        CategoryInUseException exception =
                assertThrows(
                        CategoryInUseException.class,
                        () -> categoryService.deleteCategory(1L)
                );

        assertEquals(
                "Cannot delete category with id: 1 because it has products",
                exception.getMessage()
        );

        verify(categoryRepository).findById(1L);

        verify(productRepository)
                .existsByCategoryId(1L);

        verify(categoryRepository, never())
                .delete(any(Category.class));
    }
}