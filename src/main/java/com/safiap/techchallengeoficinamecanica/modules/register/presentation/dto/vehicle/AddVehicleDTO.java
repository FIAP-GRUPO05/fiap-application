package com.safiap.techchallengeoficinamecanica.modules.register.presentation.dto.vehicle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Year;
import java.util.UUID;

public record AddVehicleDTO (
            @NotNull(message = "customerId is required")
            UUID customerId,

            @NotBlank(message = "carLicensePlate is required")
            String carLicensePlate,

            @NotBlank(message = "model is required")
            String model,

            @NotBlank(message = "manufacturer is required")
            String manufacturer,

            @NotNull(message = "kilometers is required")
            @PositiveOrZero(message = "kilometers must not be negative")
            Integer kilometers,

            @NotNull(message = "year is required")
            Year year
){
}
