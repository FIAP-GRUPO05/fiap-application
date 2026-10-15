package com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddBudgetItemsDTO(
        @NotEmpty(message = "at least one budget item is required")
        @Valid
        List<BudgetItemDTO> items
) {}
