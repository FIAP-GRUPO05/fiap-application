package com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.events;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.ServiceOrderStatus;
import com.safiap.techchallengeoficinamecanica.modules.shared.common.DomainEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record ServiceOrderStatusChangedEvent(
        UUID serviceOrderId,
        UUID customerId,
        UUID vehicleId,
        ServiceOrderStatus previousStatus,
        ServiceOrderStatus newStatus,
        // Tempo que a OS ficou no status anterior; nulo quando desconhecido (abertura ou
        // OS criada antes de o instante da última mudança passar a ser gravado)
        Duration timeInPreviousStatus,
        Instant occurredOn
) implements DomainEvent {

    public static ServiceOrderStatusChangedEvent of(UUID serviceOrderId, UUID customerId, UUID vehicleId,
                                                    ServiceOrderStatus previousStatus, ServiceOrderStatus newStatus) {
        return of(serviceOrderId, customerId, vehicleId, previousStatus, newStatus, null);
    }

    public static ServiceOrderStatusChangedEvent of(UUID serviceOrderId, UUID customerId, UUID vehicleId,
                                                    ServiceOrderStatus previousStatus, ServiceOrderStatus newStatus,
                                                    Duration timeInPreviousStatus) {
        return new ServiceOrderStatusChangedEvent(serviceOrderId, customerId, vehicleId,
                previousStatus, newStatus, timeInPreviousStatus, Instant.now());
    }
}
