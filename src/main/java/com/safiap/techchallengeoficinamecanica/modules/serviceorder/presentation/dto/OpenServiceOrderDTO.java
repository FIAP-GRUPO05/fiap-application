package com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OpenServiceOrderDTO(
        @NotNull(message = "customerId is required")
        UUID customerId,

        @NotNull(message = "vehicleId is required")
        UUID vehicleId,

        @NotBlank(message = "problemDescription is required")
        String problemDescription
) {
}
