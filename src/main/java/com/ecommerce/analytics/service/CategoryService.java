package com.ecommerce.analytics.service;

import com.ecommerce.analytics.dto.CategoryRequest;
import com.ecommerce.analytics.dto.CategoryResponse;
import com.ecommerce.analytics.entity.Category;
import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventEnvelopeFactory;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.category.CategoryCreatedEvent;
import com.ecommerce.analytics.event.payload.category.CategoryDeletedEvent;
import com.ecommerce.analytics.event.payload.category.CategoryUpdatedEvent;
import com.ecommerce.analytics.event.producer.DomainEventPublisher;
import com.ecommerce.analytics.exception.CategoryInUseException;
import com.ecommerce.analytics.exception.CategoryNotFoundException;
import com.ecommerce.analytics.repository.CategoryRepository;
import com.ecommerce.analytics.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final EventEnvelopeFactory eventEnvelopeFactory;
    private final DomainEventPublisher domainEventPublisher;

    public CategoryService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            EventEnvelopeFactory eventEnvelopeFactory,
            DomainEventPublisher domainEventPublisher
    ) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.eventEnvelopeFactory = eventEnvelopeFactory;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {

        Category category = new Category();

        category.setName(request.getName());

        Category saved = categoryRepository.save(category);

        if (eventEnvelopeFactory != null && domainEventPublisher != null) {

            CategoryCreatedEvent payload = new CategoryCreatedEvent(
                    saved.getId(),
                    saved.getName()
            );

            EventEnvelope<CategoryCreatedEvent> event =
                    eventEnvelopeFactory.create(
                            EventType.CATEGORY_CREATED,
                            "Category",
                            saved.getId(),
                            payload
                    );

            domainEventPublisher.publish(event);
        }

        return mapToResponse(saved);
    }

    @Transactional
    public CategoryResponse updateCategory(
            Long id,
            CategoryRequest request
    ) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new CategoryNotFoundException("Category not found with id: " + id)
                );

        category.setName(request.getName());

        Category saved = categoryRepository.save(category);

        if (eventEnvelopeFactory != null && domainEventPublisher != null) {

            CategoryUpdatedEvent payload = new CategoryUpdatedEvent(
                    saved.getId(),
                    saved.getName()
            );

            EventEnvelope<CategoryUpdatedEvent> event =
                    eventEnvelopeFactory.create(
                            EventType.CATEGORY_UPDATED,
                            "Category",
                            saved.getId(),
                            payload
                    );

            domainEventPublisher.publish(event);
        }

        return mapToResponse(saved);
    }

    @Transactional
    public void deleteCategory(Long id) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new CategoryNotFoundException("Category not found with id: " + id)
                );

        boolean hasProducts =
                productRepository.existsByCategoryId(id);

        if (hasProducts) {
            throw new CategoryInUseException(
                    "Cannot delete category with id: " + id + " because it has products"
            );
        }

        categoryRepository.delete(category);

        if (eventEnvelopeFactory != null && domainEventPublisher != null) {

            CategoryDeletedEvent payload =
                    new CategoryDeletedEvent(id);

            EventEnvelope<CategoryDeletedEvent> event =
                    eventEnvelopeFactory.create(
                            EventType.CATEGORY_DELETED,
                            "Category",
                            id,
                            payload
                    );

            domainEventPublisher.publish(event);
        }
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new CategoryNotFoundException("Category not found with id: " + id)
                );

        return mapToResponse(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {

        return categoryRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private CategoryResponse mapToResponse(Category category) {

        CategoryResponse response = new CategoryResponse();

        response.setId(category.getId());
        response.setName(category.getName());

        return response;
    }
}