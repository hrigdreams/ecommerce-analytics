package com.ecommerce.analytics.controller;

import com.ecommerce.analytics.dto.UserRequest;
import com.ecommerce.analytics.dto.UserResponse;
import com.ecommerce.analytics.service.UserService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@Tag(
        name = "Users",
        description = "APIs for managing ecommerce users"
)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(
            summary = "Create a new user",
            description = "Creates a new ecommerce user with the provided user information"
    )
    @PostMapping
    public UserResponse createUser(
            @Valid @RequestBody UserRequest request
    ) {
        return userService.createUser(request);
    }

    @Operation(
            summary = "Get all users",
            description = "Returns a list of all registered ecommerce users"
    )
    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers();
    }

    @Operation(
            summary = "Get user by ID",
            description = "Returns a single user using their unique ID"
    )
    @GetMapping("/{id}")
    public UserResponse getUserById(
            @PathVariable Long id
    ) {
        return userService.getUserById(id);
    }

    @Operation(
            summary = "Update user",
            description = "Updates an existing user's information using their unique ID"
    )
    @PutMapping("/{id}")
    public UserResponse updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserRequest request
    ) {
        return userService.updateUser(id, request);
    }

    @Operation(
            summary = "Delete user",
            description = "Deletes an existing user using their unique ID"
    )
    @DeleteMapping("/{id}")
    public void deleteUser(
            @PathVariable Long id
    ) {
        userService.deleteUser(id);
    }
}