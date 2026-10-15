package com.safiap.techchallengeoficinamecanica.modules.inventory.presentation.dto.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record AlterServiceDTO(
        @NotBlank(message = "name is required")
        String name,

        String description,

        @NotNull(message = "price is required")
        @PositiveOrZero(message = "price must not be negative")
        BigDecimal price
) {
}
