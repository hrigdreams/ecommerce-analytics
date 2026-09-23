package com.ecommerce.analytics.controller;

import com.ecommerce.analytics.dto.CategoryRequest;
import com.ecommerce.analytics.dto.CategoryResponse;
import com.ecommerce.analytics.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(
        name = "Categories",
        description = "APIs for creating, retrieving, updating, and deleting categories"
)
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(
            summary = "Create a category",
            description = "Creates a new category using the provided category details"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Category created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = CategoryResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid category data"
            )
    })
    @PostMapping
    public CategoryResponse createCategory(
            @Valid @RequestBody CategoryRequest request
    ) {
        return categoryService.createCategory(request);
    }

    @Operation(
            summary = "Get all categories",
            description = "Returns a list of all categories"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Categories retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = CategoryResponse.class
                            )
                    )
            )
    })
    @GetMapping
    public List<CategoryResponse> getAllCategories() {
        return categoryService.getAllCategories();
    }

    @Operation(
            summary = "Get all categories (paginated)",
            description = "Same as GET /api/v1/categories but page-able. Use this once the category list grows."
    )
    @GetMapping(params = {"page"})
    public Page<CategoryResponse> getAllCategoriesPaged(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Field to sort by", example = "name") @RequestParam(defaultValue = "id") String sortBy
    ) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(sortBy).ascending());
        return categoryService.getAllCategories(pageable);
    }

    @Operation(
            summary = "Get category by ID",
            description = "Returns a single category using its unique ID"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Category retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = CategoryResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Category not found"
            )
    })
    @GetMapping("/{id}")
    public CategoryResponse getCategoryById(
            @Parameter(
                    description = "Unique ID of the category",
                    example = "1"
            )
            @PathVariable Long id
    ) {
        return categoryService.getCategoryById(id);
    }

    @Operation(
            summary = "Update a category",
            description = "Updates an existing category using its ID"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Category updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = CategoryResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid category data"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Category not found"
            )
    })
    @PutMapping("/{id}")
    public CategoryResponse updateCategory(
            @Parameter(
                    description = "Unique ID of the category to update",
                    example = "1"
            )
            @PathVariable Long id,

            @Valid @RequestBody CategoryRequest request
    ) {
        return categoryService.updateCategory(id, request);
    }

    @Operation(
            summary = "Delete a category",
            description = "Deletes an existing category using its unique ID"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Category deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Category not found"
            )
    })
    @DeleteMapping("/{id}")
    public void deleteCategory(
            @Parameter(
                    description = "Unique ID of the category to delete",
                    example = "1"
            )
            @PathVariable Long id
    ) {
        categoryService.deleteCategory(id);
    }
}