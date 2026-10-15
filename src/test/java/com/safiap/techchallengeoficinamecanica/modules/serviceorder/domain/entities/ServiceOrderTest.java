package com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.entities;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.events.ServiceOrderStatusChangedEvent;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.Diagnosis;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.ServiceOrderPriority;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.ServiceOrderStatus;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.ConflictException;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceOrderTest {

    private ServiceOrder newOrder() {
        return ServiceOrder.open(UUID.randomUUID(), UUID.randomUUID(), "Barulho ao frear");
    }

    @Test
    @DisplayName("opens the service order with RECEIVED status and LOW priority")
    void opensWithReceivedStatusAndLowPriority() {
        ServiceOrder order = newOrder();
        assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.RECEIVED);
        assertThat(order.getPriority()).isEqualTo(ServiceOrderPriority.LOW);
        assertThat(order.getOpenedAt()).isNotNull();
        assertThat(order.getConcludedAt()).isNull();
        assertThat(order.getDiagnosis()).isNull();
    }

    @Test
    @DisplayName("walks through the full status flow up to DELIVERED")
    void walksThroughTheFullHappyPath() {
        ServiceOrder order = newOrder();

        order.startDiagnosis();
        assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.IN_DIAGNOSIS);

        order.finalizeDiagnosis(new Diagnosis("Pastilhas gastas"));
        assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.AWAITING_APPROVAL);
        assertThat(order.getDiagnosis().value()).isEqualTo("Pastilhas gastas");

        order.startExecution();
        assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.IN_EXECUTION);

        order.finalizeOrder();
        assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.FINALIZED);
        assertThat(order.getConcludedAt()).isNotNull();

        order.deliver();
        assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("fails to start diagnosis outside the RECEIVED status")
    void startDiagnosisFailsWhenNotReceived() {
        ServiceOrder order = newOrder();
        order.startDiagnosis();
        assertThatThrownBy(order::startDiagnosis).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("fails to finalize diagnosis outside the IN_DIAGNOSIS status")
    void finalizeDiagnosisFailsWhenNotInDiagnosis() {
        ServiceOrder order = newOrder();
        assertThatThrownBy(() -> order.finalizeDiagnosis(new Diagnosis("x")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("requires the diagnosis description when finalizing the diagnosis")
    void finalizeDiagnosisRequiresDiagnosis() {
        ServiceOrder order = newOrder();
        order.startDiagnosis();
        assertThatThrownBy(() -> order.finalizeDiagnosis(null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("rejecting the budget cancels the service order")
    void rejectBudgetCancelsOrder() {
        ServiceOrder order = newOrder();
        order.startDiagnosis();
        order.finalizeDiagnosis(new Diagnosis("ok"));
        order.rejectBudget();
        assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.CANCELED);
    }

    @Test
    @DisplayName("fails to start the execution of a canceled service order")
    void executionFailsWhenCanceled() {
        ServiceOrder order = newOrder();
        order.startDiagnosis();
        order.finalizeDiagnosis(new Diagnosis("ok"));
        order.rejectBudget();
        assertThatThrownBy(order::startExecution).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("fails to deliver the service order that is not FINALIZED")
    void deliverFailsWhenNotFinalized() {
        ServiceOrder order = newOrder();
        assertThatThrownBy(order::deliver).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("increases and decreases priority respecting the LOW and URGENT bounds")
    void priorityIncreasesAndDecreasesWithinBounds() {
        ServiceOrder order = newOrder();
        order.decreasePriority();
        assertThat(order.getPriority()).isEqualTo(ServiceOrderPriority.LOW);

        order.increasePriority();
        assertThat(order.getPriority()).isEqualTo(ServiceOrderPriority.NORMAL);
        order.increasePriority();
        order.increasePriority();
        assertThat(order.getPriority()).isEqualTo(ServiceOrderPriority.URGENT);
        order.increasePriority();
        assertThat(order.getPriority()).isEqualTo(ServiceOrderPriority.URGENT);
    }

    @Test
    @DisplayName("reports how long the order stayed in the previous status")
    void reportsTimeInPreviousStatus() {
        LocalDateTime enteredDiagnosisAt = LocalDateTime.now().minusMinutes(30);
        ServiceOrder order = ServiceOrder.build(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Barulho ao frear", null, ServiceOrderStatus.IN_DIAGNOSIS, enteredDiagnosisAt.minusHours(1),
                null, null, ServiceOrderPriority.LOW, enteredDiagnosisAt);

        order.finalizeDiagnosis(new Diagnosis("Pastilhas gastas"));

        ServiceOrderStatusChangedEvent event = (ServiceOrderStatusChangedEvent) order.pullDomainEvents().get(0);
        assertThat(event.previousStatus()).isEqualTo(ServiceOrderStatus.IN_DIAGNOSIS);
        assertThat(event.timeInPreviousStatus()).isBetween(Duration.ofMinutes(30), Duration.ofMinutes(31));
        assertThat(order.getStatusChangedAt()).isAfter(enteredDiagnosisAt);
    }

    @Test
    @DisplayName("leaves the time in previous status empty when the status entry time is unknown")
    void leavesTimeInPreviousStatusEmptyWhenUnknown() {
        ServiceOrder order = ServiceOrder.build(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Barulho ao frear", null, ServiceOrderStatus.RECEIVED, LocalDateTime.now(),
                null, null, ServiceOrderPriority.LOW);

        order.startDiagnosis();

        ServiceOrderStatusChangedEvent event = (ServiceOrderStatusChangedEvent) order.pullDomainEvents().get(0);
        assertThat(event.timeInPreviousStatus()).isNull();
        assertThat(order.getStatusChangedAt()).isNotNull();
    }
}
