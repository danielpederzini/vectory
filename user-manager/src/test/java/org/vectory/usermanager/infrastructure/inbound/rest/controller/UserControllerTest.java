package org.vectory.usermanager.infrastructure.inbound.rest.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.vectory.usermanager.application.service.UserService;
import org.vectory.usermanager.domain.exception.DuplicateUserException;
import org.vectory.usermanager.domain.exception.InvalidCredentialsException;
import org.vectory.usermanager.infrastructure.inbound.rest.dto.LoginRequestDto;
import org.vectory.usermanager.infrastructure.inbound.rest.dto.SignupRequestDto;
import org.vectory.usermanager.infrastructure.inbound.rest.dto.TokenResponseDto;
import org.vectory.usermanager.infrastructure.inbound.rest.dto.UserResponseDto;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("UserController")
class UserControllerTest {

    private static final String SIGNUP_ENDPOINT = "/api/v1/users/signup";
    private static final String LOGIN_ENDPOINT = "/api/v1/users/login";

    private static final String USER_ID_VALUE = "22222222-2222-2222-2222-222222222222";
    private static final UUID USER_ID = UUID.fromString(USER_ID_VALUE);
    private static final String USERNAME = "alice";
    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "s3cretpass";
    private static final String CREATION_INSTANT_VALUE = "2026-01-15T10:15:30Z";
    private static final Instant CREATION_INSTANT = Instant.parse(CREATION_INSTANT_VALUE);
    private static final String TOKEN_VALUE = "signed.jwt.token";
    private static final long EXPIRES_IN = 3600L;

    private static final String SIGNUP_BODY = """
            {
              "username": "%s",
              "email": "%s",
              "password": "%s"
            }
            """.formatted(USERNAME, EMAIL, PASSWORD);
    private static final String LOGIN_BODY = """
            {
              "username": "%s",
              "password": "%s"
            }
            """.formatted(USERNAME, PASSWORD);
    private static final String EMPTY_BODY = "{}";
    private static final String INVALID_SIGNUP_BODY = """
            {
              "username": "ab",
              "email": "not-an-email",
              "password": "short"
            }
            """;

    private static final String STATUS_PATH = "$.status";
    private static final String ERROR_FIELD_PATH_TEMPLATE = "$.errors['%s']";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static UserResponseDto buildUserResponse() {
        return UserResponseDto.builder()
                .id(USER_ID)
                .username(USERNAME)
                .email(EMAIL)
                .creationInstant(CREATION_INSTANT)
                .build();
    }

    @Test
    @DisplayName("returns 201 with the created user")
    void shouldReturnCreatedWithTheCreatedUser() throws Exception {
        when(userService.signup(any(SignupRequestDto.class))).thenReturn(buildUserResponse());

        mockMvc.perform(post(SIGNUP_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(USER_ID_VALUE))
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.creationInstant").value(CREATION_INSTANT_VALUE));
    }

    @ParameterizedTest(name = "rejected field: {0}")
    @ValueSource(strings = {"username", "email", "password"})
    @DisplayName("returns 400 reporting each missing required signup field")
    void shouldRejectSignupReportingEachMissingRequiredField(String rejectedField) throws Exception {
        mockMvc.perform(post(SIGNUP_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(ERROR_FIELD_PATH_TEMPLATE.formatted(rejectedField)).exists());

        verify(userService, never()).signup(any(SignupRequestDto.class));
    }

    @ParameterizedTest(name = "rejected field: {0}")
    @ValueSource(strings = {"username", "email", "password"})
    @DisplayName("returns 400 reporting each malformed signup field")
    void shouldRejectSignupReportingEachMalformedField(String rejectedField) throws Exception {
        mockMvc.perform(post(SIGNUP_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVALID_SIGNUP_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(ERROR_FIELD_PATH_TEMPLATE.formatted(rejectedField)).exists());

        verify(userService, never()).signup(any(SignupRequestDto.class));
    }

    @Test
    @DisplayName("maps a duplicate user failure onto 409")
    void shouldMapDuplicateUserFailureOntoConflict() throws Exception {
        when(userService.signup(any(SignupRequestDto.class)))
                .thenThrow(new DuplicateUserException(USERNAME, EMAIL, new RuntimeException("boom")));

        mockMvc.perform(post(SIGNUP_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(STATUS_PATH).value(409));
    }

    @Test
    @DisplayName("returns 200 with the minted token")
    void shouldReturnOkWithTheMintedToken() throws Exception {
        when(userService.login(any(LoginRequestDto.class)))
                .thenReturn(TokenResponseDto.builder().accessToken(TOKEN_VALUE).expiresIn(EXPIRES_IN).build());

        mockMvc.perform(post(LOGIN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(TOKEN_VALUE))
                .andExpect(jsonPath("$.expiresIn").value(EXPIRES_IN));
    }

    @ParameterizedTest(name = "rejected field: {0}")
    @ValueSource(strings = {"username", "password"})
    @DisplayName("returns 400 reporting each missing required login field")
    void shouldRejectLoginReportingEachMissingRequiredField(String rejectedField) throws Exception {
        mockMvc.perform(post(LOGIN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(ERROR_FIELD_PATH_TEMPLATE.formatted(rejectedField)).exists());

        verify(userService, never()).login(any(LoginRequestDto.class));
    }

    @Test
    @DisplayName("maps invalid credentials onto 401")
    void shouldMapInvalidCredentialsOntoUnauthorized() throws Exception {
        when(userService.login(any(LoginRequestDto.class))).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post(LOGIN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(STATUS_PATH).value(401));
    }
}
