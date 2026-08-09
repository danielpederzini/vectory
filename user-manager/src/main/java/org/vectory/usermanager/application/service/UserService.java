package org.vectory.usermanager.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final AggregateType AGGREGATE_TYPE = AggregateType.USER;

    private final UserRepository userRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    @Value("${user-manager.jwt.ttl:PT1H}")
    private Duration jwtTtl;

    @Transactional
    public UserResponseDto signup(SignupRequestDto request) {
        UUID userId = UUID.randomUUID();
        Instant currentInstant = Instant.now();
        String passwordHash = passwordEncoder.encode(request.password());

        UserEntity userEntity = UserMapper.toEntity(request, userId, passwordHash, currentInstant);

        try {
            UserEntity savedUser = userRepository.saveAndFlush(userEntity);

            outboxEventWriter.append(
                    AGGREGATE_TYPE,
                    savedUser.getId(),
                    OutboxTopics.USERS_CREATED,
                    savedUser.getId().toString(),
                    UserMapper.toCreatedEvent(savedUser)
            );

            return UserMapper.toResponseDto(savedUser);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateUserException(request.username(), request.email(), exception);
        }
    }

    public TokenResponseDto login(LoginRequestDto request) {
        UserEntity user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(jwtTtl);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("username", user.getUsername())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return TokenResponseDto.builder()
                .accessToken(token)
                .expiresIn(jwtTtl.toSeconds())
                .build();
    }
}
