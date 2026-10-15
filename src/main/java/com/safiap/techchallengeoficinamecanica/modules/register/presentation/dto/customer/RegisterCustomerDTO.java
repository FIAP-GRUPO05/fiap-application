package com.safiap.techchallengeoficinamecanica.modules.register.presentation.dto.customer;

import jakarta.validation.constraints.NotBlank;

public record RegisterCustomerDTO(
        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "email is required")
        String email,

        @NotBlank(message = "phone is required")
        String phone,

        @NotBlank(message = "cnpjCpf is required")
        String cnpjCpf
) {
}
