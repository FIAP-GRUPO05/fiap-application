package com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record AddServiceDTO(
        @NotNull(message = "itemId is required")
        UUID itemId,

        @NotBlank(message = "description is required")
        String description,

        @Positive(message = "quantity must be greater than zero")
        int quantity
) {}
