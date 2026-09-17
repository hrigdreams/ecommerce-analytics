package com.ecommerce.analytics.service;

import com.ecommerce.analytics.dto.UserRequest;
import com.ecommerce.analytics.dto.UserResponse;
import com.ecommerce.analytics.entity.User;
import com.ecommerce.analytics.exception.UserNotFoundException;
import com.ecommerce.analytics.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;


    @Test
    void createUser_shouldCreateSuccessfully() {

        UserRequest request = new UserRequest(
                "John Doe",
                "john@example.com",
                30,
                "Male",
                "Kathmandu",
                "Nepal"
        );

        User savedUser = mock(User.class);

        when(savedUser.getId()).thenReturn(1L);
        when(savedUser.getName()).thenReturn("John Doe");
        when(savedUser.getEmail()).thenReturn("john@example.com");
        when(savedUser.getAge()).thenReturn(30);
        when(savedUser.getGender()).thenReturn("Male");
        when(savedUser.getCity()).thenReturn("Kathmandu");
        when(savedUser.getCountry()).thenReturn("Nepal");

        LocalDateTime createdAt = LocalDateTime.now();
        when(savedUser.getCreatedAt()).thenReturn(createdAt);

        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("John Doe", response.getName());
        assertEquals("john@example.com", response.getEmail());
        assertEquals(30, response.getAge());
        assertEquals("Male", response.getGender());
        assertEquals("Kathmandu", response.getCity());
        assertEquals("Nepal", response.getCountry());
        assertEquals(createdAt, response.getCreatedAt());

        verify(userRepository).save(any(User.class));
    }


    @Test
    void getUserById_shouldReturnSuccessfully() {

        User user = mock(User.class);

        LocalDateTime createdAt = LocalDateTime.now();

        when(user.getId()).thenReturn(1L);
        when(user.getName()).thenReturn("John Doe");
        when(user.getEmail()).thenReturn("john@example.com");
        when(user.getAge()).thenReturn(30);
        when(user.getGender()).thenReturn("Male");
        when(user.getCity()).thenReturn("Kathmandu");
        when(user.getCountry()).thenReturn("Nepal");
        when(user.getCreatedAt()).thenReturn(createdAt);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        UserResponse response =
                userService.getUserById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("John Doe", response.getName());
        assertEquals("john@example.com", response.getEmail());
        assertEquals(30, response.getAge());
        assertEquals("Male", response.getGender());
        assertEquals("Kathmandu", response.getCity());
        assertEquals("Nepal", response.getCountry());
        assertEquals(createdAt, response.getCreatedAt());

        verify(userRepository).findById(1L);
    }


    @Test
    void getUserById_shouldThrowUserNotFoundException() {

        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());

        UserNotFoundException exception =
                assertThrows(
                        UserNotFoundException.class,
                        () -> userService.getUserById(999L)
                );

        assertEquals(
                "User not found with id: 999",
                exception.getMessage()
        );

        verify(userRepository).findById(999L);
    }


    @Test
    void getAllUsers_shouldReturnAllUsers() {

        User user1 = mock(User.class);
        User user2 = mock(User.class);

        when(user1.getId()).thenReturn(1L);
        when(user1.getName()).thenReturn("John Doe");
        when(user1.getEmail()).thenReturn("john@example.com");
        when(user1.getAge()).thenReturn(30);
        when(user1.getGender()).thenReturn("Male");
        when(user1.getCity()).thenReturn("Kathmandu");
        when(user1.getCountry()).thenReturn("Nepal");
        when(user1.getCreatedAt()).thenReturn(LocalDateTime.now());

        when(user2.getId()).thenReturn(2L);
        when(user2.getName()).thenReturn("Jane Doe");
        when(user2.getEmail()).thenReturn("jane@example.com");
        when(user2.getAge()).thenReturn(28);
        when(user2.getGender()).thenReturn("Female");
        when(user2.getCity()).thenReturn("Pokhara");
        when(user2.getCountry()).thenReturn("Nepal");
        when(user2.getCreatedAt()).thenReturn(LocalDateTime.now());

        when(userRepository.findAll())
                .thenReturn(List.of(user1, user2));

        List<UserResponse> responses =
                userService.getAllUsers();

        assertNotNull(responses);
        assertEquals(2, responses.size());

        assertEquals(1L, responses.get(0).getId());
        assertEquals("John Doe", responses.get(0).getName());
        assertEquals("john@example.com", responses.get(0).getEmail());

        assertEquals(2L, responses.get(1).getId());
        assertEquals("Jane Doe", responses.get(1).getName());
        assertEquals("jane@example.com", responses.get(1).getEmail());

        verify(userRepository).findAll();
    }


    @Test
    void updateUser_shouldUpdateSuccessfully() {

        UserRequest request = new UserRequest(
                "Updated John",
                "updated@example.com",
                35,
                "Male",
                "Pokhara",
                "Nepal"
        );

        User user = mock(User.class);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(1L);
        when(user.getName()).thenReturn("Updated John");
        when(user.getEmail()).thenReturn("updated@example.com");
        when(user.getAge()).thenReturn(35);
        when(user.getGender()).thenReturn("Male");
        when(user.getCity()).thenReturn("Pokhara");
        when(user.getCountry()).thenReturn("Nepal");
        when(user.getCreatedAt()).thenReturn(LocalDateTime.now());

        when(userRepository.save(user))
                .thenReturn(user);

        UserResponse response =
                userService.updateUser(1L, request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Updated John", response.getName());
        assertEquals("updated@example.com", response.getEmail());
        assertEquals(35, response.getAge());
        assertEquals("Male", response.getGender());
        assertEquals("Pokhara", response.getCity());
        assertEquals("Nepal", response.getCountry());

        verify(userRepository).findById(1L);

        verify(user).setName("Updated John");
        verify(user).setEmail("updated@example.com");
        verify(user).setAge(35);
        verify(user).setGender("Male");
        verify(user).setCity("Pokhara");
        verify(user).setCountry("Nepal");

        verify(userRepository).save(user);
    }


    @Test
    void updateUser_shouldThrowUserNotFoundException() {

        UserRequest request = new UserRequest(
                "Updated John",
                "updated@example.com",
                35,
                "Male",
                "Pokhara",
                "Nepal"
        );

        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());

        UserNotFoundException exception =
                assertThrows(
                        UserNotFoundException.class,
                        () -> userService.updateUser(999L, request)
                );

        assertEquals(
                "User not found with id: 999",
                exception.getMessage()
        );

        verify(userRepository).findById(999L);

        verify(userRepository, never())
                .save(any(User.class));
    }


    @Test
    void deleteUser_shouldDeleteSuccessfully() {

        User user = mock(User.class);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        userService.deleteUser(1L);

        verify(userRepository).findById(1L);
        verify(userRepository).delete(user);
    }


    @Test
    void deleteUser_shouldThrowUserNotFoundException() {

        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());

        UserNotFoundException exception =
                assertThrows(
                        UserNotFoundException.class,
                        () -> userService.deleteUser(999L)
                );

        assertEquals(
                "User not found with id: 999",
                exception.getMessage()
        );

        verify(userRepository).findById(999L);

        verify(userRepository, never())
                .delete(any(User.class));
    }
}