package com.ecommerce.analytics.controller;

import com.ecommerce.analytics.dto.UserRequest;
import com.ecommerce.analytics.dto.UserResponse;
import com.ecommerce.analytics.exception.GlobalExceptionHandler;
import com.ecommerce.analytics.exception.UserNotFoundException;
import com.ecommerce.analytics.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private UserService userService;


    // ============================================================
    // CREATE USER
    // ============================================================

    @Test
    void createUser_shouldReturnCreatedUser() throws Exception {

        UserRequest request = new UserRequest(
                "John Doe",
                "john@example.com",
                30,
                "Male",
                "Kathmandu",
                "Nepal"
        );

        UserResponse response = new UserResponse(
                1L,
                "John Doe",
                "john@example.com",
                30,
                "Male",
                "Kathmandu",
                "Nepal",
                LocalDateTime.now()
        );

        when(userService.createUser(any(UserRequest.class)))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.age").value(30))
                .andExpect(jsonPath("$.gender").value("Male"))
                .andExpect(jsonPath("$.city").value("Kathmandu"))
                .andExpect(jsonPath("$.country").value("Nepal"));

        verify(userService)
                .createUser(any(UserRequest.class));
    }


    @Test
    void createUser_shouldReturnBadRequest_whenNameMissing()
            throws Exception {

        UserRequest request = new UserRequest(
                "",
                "john@example.com",
                30,
                "Male",
                "Kathmandu",
                "Nepal"
        );

        mockMvc.perform(
                        post("/api/v1/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());

        verify(userService, never())
                .createUser(any(UserRequest.class));
    }


    @Test
    void createUser_shouldReturnBadRequest_whenEmailInvalid()
            throws Exception {

        UserRequest request = new UserRequest(
                "John Doe",
                "invalid-email",
                30,
                "Male",
                "Kathmandu",
                "Nepal"
        );

        mockMvc.perform(
                        post("/api/v1/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());

        verify(userService, never())
                .createUser(any(UserRequest.class));
    }


    @Test
    void createUser_shouldReturnBadRequest_whenAgeInvalid()
            throws Exception {

        UserRequest request = new UserRequest(
                "John Doe",
                "john@example.com",
                0,
                "Male",
                "Kathmandu",
                "Nepal"
        );

        mockMvc.perform(
                        post("/api/v1/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());

        verify(userService, never())
                .createUser(any(UserRequest.class));
    }


    // ============================================================
    // GET ALL USERS
    // ============================================================

    @Test
    void getAllUsers_shouldReturnUsers() throws Exception {

        UserResponse user1 = new UserResponse(
                1L,
                "John Doe",
                "john@example.com",
                30,
                "Male",
                "Kathmandu",
                "Nepal",
                LocalDateTime.now()
        );

        UserResponse user2 = new UserResponse(
                2L,
                "Jane Doe",
                "jane@example.com",
                28,
                "Female",
                "Pokhara",
                "Nepal",
                LocalDateTime.now()
        );

        when(userService.getAllUsers())
                .thenReturn(List.of(user1, user2));

        mockMvc.perform(
                        get("/api/v1/users")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("John Doe"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("Jane Doe"));

        verify(userService)
                .getAllUsers();
    }


    @Test
    void getAllUsers_shouldReturnEmptyList() throws Exception {

        when(userService.getAllUsers())
                .thenReturn(List.of());

        mockMvc.perform(
                        get("/api/v1/users")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(userService)
                .getAllUsers();
    }


    // ============================================================
    // GET USER BY ID
    // ============================================================

    @Test
    void getUserById_shouldReturnUser() throws Exception {

        UserResponse response = new UserResponse(
                1L,
                "John Doe",
                "john@example.com",
                30,
                "Male",
                "Kathmandu",
                "Nepal",
                LocalDateTime.now()
        );

        when(userService.getUserById(1L))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/users/1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.age").value(30))
                .andExpect(jsonPath("$.city").value("Kathmandu"));

        verify(userService)
                .getUserById(1L);
    }


    @Test
    void getUserById_shouldThrowUserNotFoundException()
            throws Exception {

        when(userService.getUserById(999L))
                .thenThrow(
                        new UserNotFoundException(
                                "User not found with id: 999"
                        )
                );

        mockMvc.perform(
                        get("/api/v1/users/999")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.message")
                                .value("User not found with id: 999")
                );

        verify(userService)
                .getUserById(999L);
    }


    // ============================================================
    // UPDATE USER
    // ============================================================

    @Test
    void updateUser_shouldReturnUpdatedUser()
            throws Exception {

        UserRequest request = new UserRequest(
                "Updated John",
                "updated@example.com",
                35,
                "Male",
                "Pokhara",
                "Nepal"
        );

        UserResponse response = new UserResponse(
                1L,
                "Updated John",
                "updated@example.com",
                35,
                "Male",
                "Pokhara",
                "Nepal",
                LocalDateTime.now()
        );

        when(
                userService.updateUser(
                        eq(1L),
                        any(UserRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        put("/api/v1/users/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(
                        jsonPath("$.name")
                                .value("Updated John")
                )
                .andExpect(
                        jsonPath("$.email")
                                .value("updated@example.com")
                )
                .andExpect(
                        jsonPath("$.age")
                                .value(35)
                )
                .andExpect(
                        jsonPath("$.city")
                                .value("Pokhara")
                );

        verify(userService)
                .updateUser(
                        eq(1L),
                        any(UserRequest.class)
                );
    }


    @Test
    void updateUser_shouldReturnBadRequest_whenRequestInvalid()
            throws Exception {

        UserRequest request = new UserRequest(
                "",
                "invalid-email",
                0,
                "Male",
                "",
                ""
        );

        mockMvc.perform(
                        put("/api/v1/users/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isBadRequest());

        verify(userService, never())
                .updateUser(
                        eq(1L),
                        any(UserRequest.class)
                );
    }


    @Test
    void updateUser_shouldThrowUserNotFoundException()
            throws Exception {

        UserRequest request = new UserRequest(
                "Updated John",
                "updated@example.com",
                35,
                "Male",
                "Pokhara",
                "Nepal"
        );

        when(
                userService.updateUser(
                        eq(999L),
                        any(UserRequest.class)
                )
        ).thenThrow(
                new UserNotFoundException(
                        "User not found with id: 999"
                )
        );

        mockMvc.perform(
                        put("/api/v1/users/999")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.message")
                                .value("User not found with id: 999")
                );

        verify(userService)
                .updateUser(
                        eq(999L),
                        any(UserRequest.class)
                );
    }


    // ============================================================
    // DELETE USER
    // ============================================================

    @Test
    void deleteUser_shouldReturnSuccess() throws Exception {

        doNothing()
                .when(userService)
                .deleteUser(1L);

        mockMvc.perform(
                        delete("/api/v1/users/1")
                )
                .andExpect(status().isOk());

        verify(userService)
                .deleteUser(1L);
    }


    @Test
    void deleteUser_shouldThrowUserNotFoundException()
            throws Exception {

        doThrow(
                new UserNotFoundException(
                        "User not found with id: 999"
                )
        )
                .when(userService)
                .deleteUser(999L);

        mockMvc.perform(
                        delete("/api/v1/users/999")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.message")
                                .value("User not found with id: 999")
                );

        verify(userService)
                .deleteUser(999L);
    }
}