package com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record FinalizeDiagnosisDTO(
        @NotBlank(message = "diagnosis is required")
        String diagnosis,

        @Valid
        List<BudgetItemDTO> items
) {}
