package org.vectory.usermanager.application.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;
import org.vectory.usermanager.application.event.UserCreatedEvent;
import org.vectory.usermanager.application.mapper.UserMapper;
import org.vectory.usermanager.domain.enums.AggregateType;
import org.vectory.usermanager.domain.exception.DuplicateUserException;
import org.vectory.usermanager.domain.exception.InvalidCredentialsException;
import org.vectory.usermanager.infrastructure.inbound.rest.dto.LoginRequestDto;
import org.vectory.usermanager.infrastructure.inbound.rest.dto.SignupRequestDto;
import org.vectory.usermanager.infrastructure.inbound.rest.dto.TokenResponseDto;
import org.vectory.usermanager.infrastructure.inbound.rest.dto.UserResponseDto;
import org.vectory.usermanager.infrastructure.outbound.messaging.OutboxEventWriter;
import org.vectory.usermanager.infrastructure.outbound.messaging.OutboxTopics;
import org.vectory.usermanager.infrastructure.outbound.persistence.entity.UserEntity;
import org.vectory.usermanager.infrastructure.outbound.persistence.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    private static final UUID PERSISTED_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String USERNAME = "alice";
    private static final String EMAIL = "alice@example.com";
    private static final String RAW_PASSWORD = "s3cretpass";
    private static final String PASSWORD_HASH = "$2a$10$hashedvalue";
    private static final String CONSTRAINT_VIOLATION_MESSAGE = "uq_users_username violated";
    private static final String TOKEN_VALUE = "signed.jwt.token";
    private static final Duration JWT_TTL = Duration.ofHours(1);

    private static final SignupRequestDto SIGNUP_REQUEST = new SignupRequestDto(USERNAME, EMAIL, RAW_PASSWORD);
    private static final LoginRequestDto LOGIN_REQUEST = new LoginRequestDto(USERNAME, RAW_PASSWORD);
    private static final UserEntity MAPPED_ENTITY = UserEntity.builder().build();
    private static final UserEntity SAVED_ENTITY = UserEntity.builder()
            .id(PERSISTED_USER_ID)
            .username(USERNAME)
            .passwordHash(PASSWORD_HASH)
            .build();
    private static final UserCreatedEvent CREATED_EVENT =
            UserCreatedEvent.builder().userId(PERSISTED_USER_ID).build();
    private static final UserResponseDto RESPONSE =
            UserResponseDto.builder().id(PERSISTED_USER_ID).build();

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtEncoder jwtEncoder;

    @InjectMocks
    private UserService userService;

    private MockedStatic<UserMapper> userMapper;

    @Captor
    private ArgumentCaptor<UUID> idCaptor;

    @Captor
    private ArgumentCaptor<Instant> instantCaptor;

    @BeforeEach
    void openMapperMockAndTtl() {
        userMapper = mockStatic(UserMapper.class);
        ReflectionTestUtils.setField(userService, "jwtTtl", JWT_TTL);
    }

    @AfterEach
    void closeMapperMock() {
        userMapper.close();
    }

    private void stubSignupHappyPath() {
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(PASSWORD_HASH);
        userMapper.when(() -> UserMapper.toEntity(eq(SIGNUP_REQUEST), any(UUID.class), eq(PASSWORD_HASH), any(Instant.class)))
                .thenReturn(MAPPED_ENTITY);
        when(userRepository.saveAndFlush(MAPPED_ENTITY)).thenReturn(SAVED_ENTITY);
        userMapper.when(() -> UserMapper.toCreatedEvent(SAVED_ENTITY)).thenReturn(CREATED_EVENT);
        userMapper.when(() -> UserMapper.toResponseDto(SAVED_ENTITY)).thenReturn(RESPONSE);
    }

    @Test
    @DisplayName("encodes the password and maps the request to an entity with a generated id and instant, then persists it")
    void shouldEncodePasswordMapRequestAndPersistIt() {
        stubSignupHappyPath();

        Instant before = Instant.now();
        userService.signup(SIGNUP_REQUEST);
        Instant after = Instant.now();

        verify(passwordEncoder).encode(RAW_PASSWORD);
        userMapper.verify(() -> UserMapper.toEntity(
                eq(SIGNUP_REQUEST), idCaptor.capture(), eq(PASSWORD_HASH), instantCaptor.capture()));
        assertThat(idCaptor.getValue()).isNotNull();
        assertThat(instantCaptor.getValue()).isBetween(before, after);
        verify(userRepository).saveAndFlush(MAPPED_ENTITY);
    }

    @Test
    @DisplayName("returns the response produced by the mapper from the persisted entity")
    void shouldReturnResponseFromMapper() {
        stubSignupHappyPath();

        UserResponseDto response = userService.signup(SIGNUP_REQUEST);

        assertThat(response).isSameAs(RESPONSE);
        userMapper.verify(() -> UserMapper.toResponseDto(SAVED_ENTITY));
    }

    @Test
    @DisplayName("writes a users.created outbox event mapped from the persisted entity and keyed by its id")
    void shouldWriteUserCreatedOutboxEvent() {
        stubSignupHappyPath();

        userService.signup(SIGNUP_REQUEST);

        userMapper.verify(() -> UserMapper.toCreatedEvent(SAVED_ENTITY));
        verify(outboxEventWriter).append(
                eq(AggregateType.USER),
                eq(PERSISTED_USER_ID),
                eq(OutboxTopics.USERS_CREATED),
                eq(PERSISTED_USER_ID.toString()),
                eq(CREATED_EVENT)
        );
    }

    @Test
    @DisplayName("translates a uniqueness violation into a duplicate user failure without emitting an event")
    void shouldTranslateUniquenessViolationIntoDuplicateUserFailure() {
        DataIntegrityViolationException cause =
                new DataIntegrityViolationException(CONSTRAINT_VIOLATION_MESSAGE);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(PASSWORD_HASH);
        userMapper.when(() -> UserMapper.toEntity(eq(SIGNUP_REQUEST), any(UUID.class), eq(PASSWORD_HASH), any(Instant.class)))
                .thenReturn(MAPPED_ENTITY);
        when(userRepository.saveAndFlush(MAPPED_ENTITY)).thenThrow(cause);

        assertThatExceptionOfType(DuplicateUserException.class)
                .isThrownBy(() -> userService.signup(SIGNUP_REQUEST))
                .withCause(cause)
                .satisfies(exception -> assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(outboxEventWriter);
        userMapper.verify(() -> UserMapper.toCreatedEvent(any()), never());
        userMapper.verify(() -> UserMapper.toResponseDto(any()), never());
    }

    @Test
    @DisplayName("mints a token for valid credentials without writing to the outbox")
    void shouldMintTokenForValidCredentials() {
        Jwt jwt = Jwt.withTokenValue(TOKEN_VALUE)
                .header("alg", "HS256")
                .subject(PERSISTED_USER_ID.toString())
                .claim("username", USERNAME)
                .build();
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(SAVED_ENTITY));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);

        TokenResponseDto token = userService.login(LOGIN_REQUEST);

        assertThat(token.accessToken()).isEqualTo(TOKEN_VALUE);
        assertThat(token.expiresIn()).isEqualTo(JWT_TTL.toSeconds());
        verifyNoInteractions(outboxEventWriter);
    }

    @Test
    @DisplayName("rejects login when the username is unknown without touching the encoder")
    void shouldRejectLoginForUnknownUsername() {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> userService.login(LOGIN_REQUEST))
                .satisfies(exception -> assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(jwtEncoder);
        verifyNoInteractions(outboxEventWriter);
    }

    @Test
    @DisplayName("rejects login when the password does not match without minting a token")
    void shouldRejectLoginForWrongPassword() {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(SAVED_ENTITY));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(false);

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> userService.login(LOGIN_REQUEST))
                .satisfies(exception -> assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(jwtEncoder);
        verifyNoInteractions(outboxEventWriter);
    }
}
