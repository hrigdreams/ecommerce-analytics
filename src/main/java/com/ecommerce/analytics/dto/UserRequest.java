package com.ecommerce.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(
        name = "UserRequest",
        description = "Request payload used to create or update a user"
)
public class UserRequest {

    @Schema(
            description = "Full name of the user",
            example = "John Doe",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "User name is required")
    private String name;

    @Schema(
            description = "Email address of the user",
            example = "john@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @Schema(
            description = "Age of the user. Must be greater than 0",
            example = "30",
            minimum = "1",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Age is required")
    @Positive(message = "Age must be greater than 0")
    private Integer age;

    @Schema(
            description = "Gender of the user",
            example = "Male"
    )
    private String gender;

    @Schema(
            description = "City where the user lives",
            example = "Kathmandu",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "City is required")
    private String city;

    @Schema(
            description = "Country where the user lives",
            example = "Nepal",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Country is required")
    private String country;

    public UserRequest() {
    }

    public UserRequest(
            String name,
            String email,
            Integer age,
            String gender,
            String city,
            String country
    ) {
        this.name = name;
        this.email = email;
        this.age = age;
        this.gender = gender;
        this.city = city;
        this.country = country;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}