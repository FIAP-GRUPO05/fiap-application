package com.safiap.techchallengeoficinamecanica.modules.inventory.presentation.dto.part;

import jakarta.validation.constraints.Positive;

public record StockMovementDTO(
        @Positive(message = "amount must be greater than zero")
        int amount
) {
}
