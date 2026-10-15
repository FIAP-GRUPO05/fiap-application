package com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record OpenServiceOrderWithBudgetDTO(
        @NotNull UUID customerId,
        @NotNull UUID vehicleId,
        @NotNull String problemDescription,
        @NotEmpty @Valid List<BudgetItemDTO> items
) {}