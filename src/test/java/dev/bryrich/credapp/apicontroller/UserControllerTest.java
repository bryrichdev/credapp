package dev.bryrich.credapp.apicontroller;

import dev.bryrich.credapp.entity.Role;
import dev.bryrich.credapp.entity.User;
import dev.bryrich.credapp.exception.EmailAlreadyExistsException;
import dev.bryrich.credapp.exception.UserNotFoundException;
import dev.bryrich.credapp.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@WithMockUser(roles = "ADMIN")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private User savedUser() {
        User user = new User("dev@credapp.local", "hashed-value");
        user.setFullName("Dev Admin");
        user.setRole(Role.ADMIN);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    @Test
    void getByIdReturnsTheUserWithoutThePasswordHash() throws Exception {
        when(userService.findById(1L)).thenReturn(savedUser());

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("dev@credapp.local"))
                .andExpect(jsonPath("$.fullName").value("Dev Admin"))
                .andExpect(jsonPath("$.role").value("admin"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void getByIdReturnsProblemDetailWhenTheUserIsMissing() throws Exception {
        when(userService.findById(99L)).thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(get("/api/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/api/users/99"));
    }

    @Test
    void createReturns201WithALocationHeader() throws Exception {
        when(userService.create(any(), any(), any(), any())).thenReturn(savedUser());

        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dev@credapp.local",
                                  "password": "averylongpassword",
                                  "fullName": "Dev Admin",
                                  "role": "admin"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/users/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(userService).create(
                eq("dev@credapp.local"), eq("averylongpassword"), eq("Dev Admin"), eq(Role.ADMIN));
    }

    @Test
    void createReportsEveryInvalidField() throws Exception {
        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "short",
                                  "fullName": "Dev Admin",
                                  "role": "admin"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").isArray())
                .andExpect(jsonPath("$.errors.password").isArray());
    }

    @Test
    void createRejectsAnUnknownRole() throws Exception {
        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dev@credapp.local",
                                  "password": "averylongpassword",
                                  "role": "supervisor"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns409WhenTheEmailIsTaken() throws Exception {
        when(userService.create(any(), any(), any(), any()))
                .thenThrow(new EmailAlreadyExistsException("dev@credapp.local"));

        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dev@credapp.local",
                                  "password": "averylongpassword",
                                  "role": "admin"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already exists"))
                .andExpect(jsonPath("$.status").value(409));
    }
}
