package com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.entities;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.events.ServiceOrderStatusChangedEvent;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.Diagnosis;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.ServiceOrderPriority;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.ServiceOrderStatus;
import com.safiap.techchallengeoficinamecanica.modules.shared.common.AggregateRoot;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.ConflictException;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.DomainException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

public class ServiceOrder extends AggregateRoot {

    private UUID serviceOrderId;
    private UUID customerId;
    private UUID vehicleId;
    private String problemDescription;
    private Diagnosis diagnosis;
    private ServiceOrderStatus status;
    private LocalDateTime openedAt;
    private LocalDateTime executionStartedAt;
    private LocalDateTime concludedAt;
    private ServiceOrderPriority priority;
    // Instante em que a OS entrou no status atual: base do tempo por status
    private LocalDateTime statusChangedAt;

    private ServiceOrder(UUID serviceOrderId, UUID customerId, UUID vehicleId,
                         String problemDescription, Diagnosis diagnosis, ServiceOrderStatus status,
                         LocalDateTime openedAt, LocalDateTime executionStartedAt,
                         LocalDateTime concludedAt, ServiceOrderPriority priority,
                         LocalDateTime statusChangedAt) {
        this.serviceOrderId = serviceOrderId;
        this.customerId = customerId;
        this.vehicleId = vehicleId;
        this.problemDescription = problemDescription;
        this.diagnosis = diagnosis;
        this.status = status;
        this.openedAt = openedAt;
        this.executionStartedAt = executionStartedAt;
        this.concludedAt = concludedAt;
        this.priority = priority;
        this.statusChangedAt = statusChangedAt;
    }

    public static ServiceOrder open(UUID customerId, UUID vehicleId, String problemDescription) {
        LocalDateTime now = LocalDateTime.now();
        ServiceOrder serviceOrder = new ServiceOrder(UUID.randomUUID(), customerId, vehicleId,
                problemDescription, null, ServiceOrderStatus.RECEIVED, now, null, null,
                ServiceOrderPriority.LOW, now);
        serviceOrder.registerDomainEvent(ServiceOrderStatusChangedEvent.of(
                serviceOrder.serviceOrderId, serviceOrder.customerId, serviceOrder.vehicleId,
                null, ServiceOrderStatus.RECEIVED));
        return serviceOrder;
    }

    public static ServiceOrder build(UUID serviceOrderId, UUID customerId, UUID vehicleId,
                                     String problemDescription, Diagnosis diagnosis, ServiceOrderStatus status,
                                     LocalDateTime openedAt, LocalDateTime executionStartedAt,
                                     LocalDateTime concludedAt, ServiceOrderPriority priority) {
        return build(serviceOrderId, customerId, vehicleId, problemDescription, diagnosis, status,
                openedAt, executionStartedAt, concludedAt, priority, null);
    }

    public static ServiceOrder build(UUID serviceOrderId, UUID customerId, UUID vehicleId,
                                     String problemDescription, Diagnosis diagnosis, ServiceOrderStatus status,
                                     LocalDateTime openedAt, LocalDateTime executionStartedAt,
                                     LocalDateTime concludedAt, ServiceOrderPriority priority,
                                     LocalDateTime statusChangedAt) {
        return new ServiceOrder(serviceOrderId, customerId, vehicleId, problemDescription, diagnosis, status,
                openedAt, executionStartedAt, concludedAt, priority, statusChangedAt);
    }

    public void increasePriority() {
        this.priority = this.priority.increase();
    }

    public void decreasePriority() {
        this.priority = this.priority.decrease();
    }

    public void startDiagnosis() {
        if (this.status != ServiceOrderStatus.RECEIVED)
            throw new ConflictException("Service order must be in RECEIVED status to start diagnosis");
        changeStatus(ServiceOrderStatus.IN_DIAGNOSIS);
    }

    public void finalizeDiagnosis(Diagnosis diagnosis) {
        if (this.status != ServiceOrderStatus.IN_DIAGNOSIS)
            throw new ConflictException("Service order must be in IN_DIAGNOSIS status to finalize diagnosis");
        if (diagnosis == null)
            throw new DomainException("Diagnosis description is required to finalize diagnosis");
        this.diagnosis = diagnosis;
        changeStatus(ServiceOrderStatus.AWAITING_APPROVAL);
    }

    public void startExecution() {
        if (this.status != ServiceOrderStatus.AWAITING_APPROVAL)
            throw new ConflictException("Service order must be in AWAITING_APPROVAL status to start execution");
        this.executionStartedAt = LocalDateTime.now();
        changeStatus(ServiceOrderStatus.IN_EXECUTION);
    }

    public void rejectBudget() {
        if (this.status == ServiceOrderStatus.CANCELED)
            return;
        if (this.status != ServiceOrderStatus.AWAITING_APPROVAL)
            throw new ConflictException("Service order must be in AWAITING_APPROVAL status to reject budget");
        changeStatus(ServiceOrderStatus.CANCELED);
    }

    public void finalizeOrder() {
        if (this.status != ServiceOrderStatus.IN_EXECUTION)
            throw new ConflictException("Service order must be in IN_EXECUTION status to finalize");
        this.concludedAt = LocalDateTime.now();
        changeStatus(ServiceOrderStatus.FINALIZED);
    }

    public void deliver() {
        if (this.status != ServiceOrderStatus.FINALIZED)
            throw new ConflictException("Service order must be in FINALIZED status to deliver");
        changeStatus(ServiceOrderStatus.DELIVERED);
    }

    private void changeStatus(ServiceOrderStatus newStatus) {
        ServiceOrderStatus previousStatus = this.status;
        LocalDateTime now = LocalDateTime.now();
        Duration timeInPreviousStatus = statusChangedAt != null ? Duration.between(statusChangedAt, now) : null;
        this.status = newStatus;
        this.statusChangedAt = now;
        registerDomainEvent(ServiceOrderStatusChangedEvent.of(
                serviceOrderId, customerId, vehicleId, previousStatus, newStatus, timeInPreviousStatus));
    }

    public UUID getServiceOrderId() { return serviceOrderId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getVehicleId() { return vehicleId; }
    public String getProblemDescription() { return problemDescription; }
    public Diagnosis getDiagnosis() { return diagnosis; }
    public ServiceOrderStatus getStatus() { return status; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public LocalDateTime getExecutionStartedAt() { return executionStartedAt; }
    public LocalDateTime getConcludedAt() { return concludedAt; }
    public ServiceOrderPriority getPriority() { return priority;}
    public LocalDateTime getStatusChangedAt() { return statusChangedAt; }
}
