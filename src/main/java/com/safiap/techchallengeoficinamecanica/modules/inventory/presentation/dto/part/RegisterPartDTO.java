package com.safiap.techchallengeoficinamecanica.modules.inventory.presentation.dto.part;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record RegisterPartDTO(
        @NotBlank(message = "name is required")
        String name,

        String description,

        @PositiveOrZero(message = "quantity must not be negative")
        int quantity,

        @NotNull(message = "price is required")
        @PositiveOrZero(message = "price must not be negative")
        BigDecimal price
) {
}
