package com.safiap.techchallengeoficinamecanica.modules.register.application.responses.customer;

import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.CustomerStatus;

import java.util.UUID;

public record CustomerStatusResponse(
        UUID id,
        String name,
        CustomerStatus status
) {
}
