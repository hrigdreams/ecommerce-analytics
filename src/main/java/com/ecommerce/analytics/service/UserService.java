package com.ecommerce.analytics.service;

import com.ecommerce.analytics.dto.UserRequest;
import com.ecommerce.analytics.dto.UserResponse;
import com.ecommerce.analytics.entity.User;
import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventEnvelopeFactory;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.user.UserCreatedEvent;
import com.ecommerce.analytics.event.payload.user.UserDeletedEvent;
import com.ecommerce.analytics.event.payload.user.UserUpdatedEvent;
import com.ecommerce.analytics.event.producer.DomainEventPublisher;
import com.ecommerce.analytics.exception.UserNotFoundException;
import com.ecommerce.analytics.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final EventEnvelopeFactory eventEnvelopeFactory;
    private final DomainEventPublisher domainEventPublisher;

    public UserService(
            UserRepository userRepository,
            EventEnvelopeFactory eventEnvelopeFactory,
            DomainEventPublisher domainEventPublisher
    ) {
        this.userRepository = userRepository;
        this.eventEnvelopeFactory = eventEnvelopeFactory;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {

        User user = new User();

        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setAge(request.getAge());
        user.setGender(request.getGender());
        user.setCity(request.getCity());
        user.setCountry(request.getCountry());

        User saved = userRepository.save(user);

        if (eventEnvelopeFactory != null && domainEventPublisher != null) {

            UserCreatedEvent payload = new UserCreatedEvent(
                    saved.getId(),
                    saved.getName(),
                    saved.getEmail(),
                    saved.getAge(),
                    saved.getGender(),
                    saved.getCity(),
                    saved.getCountry()
            );

            EventEnvelope<UserCreatedEvent> event =
                    eventEnvelopeFactory.create(
                            EventType.USER_CREATED,
                            "User",
                            saved.getId(),
                            payload
                    );

            domainEventPublisher.publish(event);
        }

        return mapToResponse(saved);
    }

    @Transactional
    public UserResponse updateUser(Long id, UserRequest request) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found with id: " + id)
                );

        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setAge(request.getAge());
        user.setGender(request.getGender());
        user.setCity(request.getCity());
        user.setCountry(request.getCountry());

        User saved = userRepository.save(user);

        if (eventEnvelopeFactory != null && domainEventPublisher != null) {

            UserUpdatedEvent payload = new UserUpdatedEvent(
                    saved.getId()
            );

            EventEnvelope<UserUpdatedEvent> event =
                    eventEnvelopeFactory.create(
                            EventType.USER_UPDATED,
                            "User",
                            saved.getId(),
                            payload
                    );

            domainEventPublisher.publish(event);
        }

        return mapToResponse(saved);
    }

    @Transactional
    public void deleteUser(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found with id: " + id)
                );

        userRepository.delete(user);

        if (eventEnvelopeFactory != null && domainEventPublisher != null) {

            UserDeletedEvent payload = new UserDeletedEvent(
                    id
            );

            EventEnvelope<UserDeletedEvent> event =
                    eventEnvelopeFactory.create(
                            EventType.USER_DELETED,
                            "User",
                            id,
                            payload
                    );

            domainEventPublisher.publish(event);
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found with id: " + id)
                );

        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {

        return userRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private UserResponse mapToResponse(User user) {

        UserResponse response = new UserResponse();

        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setAge(user.getAge());
        response.setGender(user.getGender());
        response.setCity(user.getCity());
        response.setCountry(user.getCountry());
        response.setCreatedAt(user.getCreatedAt());

        return response;
    }
}