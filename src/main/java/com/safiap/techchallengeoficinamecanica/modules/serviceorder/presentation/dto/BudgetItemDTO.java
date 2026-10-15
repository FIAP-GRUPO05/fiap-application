package com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.dto;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.BudgetItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record BudgetItemDTO(
        @NotNull(message = "type is required (PART or SERVICE)")
        BudgetItemType type,

        @NotNull(message = "itemId is required")
        UUID itemId,

        @NotBlank(message = "description is required")
        String description,

        @Positive(message = "quantity must be greater than zero")
        int quantity
) {}
