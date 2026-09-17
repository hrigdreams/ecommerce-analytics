package com.ecommerce.analytics.event.payload.user;

public class UserCreatedEvent {

    private Long userId;
    private String name;
    private String email;
    private Integer age;
    private String gender;
    private String city;
    private String country;

    public UserCreatedEvent() {
    }

    public UserCreatedEvent(
            Long userId,
            String name,
            String email,
            Integer age,
            String gender,
            String city,
            String country
    ) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.age = age;
        this.gender = gender;
        this.city = city;
        this.country = country;
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Integer getAge() { return age; }
    public String getGender() { return gender; }
    public String getCity() { return city; }
    public String getCountry() { return country; }

    public void setUserId(Long userId) { this.userId = userId; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setAge(Integer age) { this.age = age; }
    public void setGender(String gender) { this.gender = gender; }
    public void setCity(String city) { this.city = city; }
    public void setCountry(String country) { this.country = country; }
}