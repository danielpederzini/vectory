package org.vectory.usermanager.infrastructure.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(

        @NotBlank
        String username,

        @NotBlank
        String password
) {
}
