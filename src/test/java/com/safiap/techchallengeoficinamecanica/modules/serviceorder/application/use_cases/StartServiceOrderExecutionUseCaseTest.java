package com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.ports.PartStockPort;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.responses.ServiceOrderResponse;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.entities.Budget;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.entities.ServiceOrder;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.repositories.BudgetRepository;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.repositories.ServiceOrderRepository;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.ServiceOrderPriority;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.ServiceOrderStatus;
import com.safiap.techchallengeoficinamecanica.modules.shared.domain.events.DomainEventPublisher;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.ConflictException;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartServiceOrderExecutionUseCaseTest {

    private final ServiceOrderRepository serviceOrderRepository = mock(ServiceOrderRepository.class);
    private final BudgetRepository budgetRepository = mock(BudgetRepository.class);
    private final PartStockPort partStockPort = mock(PartStockPort.class);
    private final DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
    private final StartServiceOrderExecutionUseCase useCase = new StartServiceOrderExecutionUseCase(
            serviceOrderRepository, budgetRepository, partStockPort, domainEventPublisher);

    /** A execucao so e liberada com o orcamento APROVADO pelo cliente. */
    private Budget approvedBudget(UUID serviceOrderId, UUID partId) {
        Budget budget = Budget.create(serviceOrderId);
        if (partId != null) {
            budget.addPart(partId, "Filtro", 3, new BigDecimal("40.00"));
        } else {
            budget.addService(UUID.randomUUID(), "Mao de obra", 1, new BigDecimal("100.00"));
        }
        budget.finalizeBudget();
        budget.approve();
        return budget;
    }

    private ServiceOrder order(UUID id, ServiceOrderStatus status) {
        return ServiceOrder.build(id, UUID.randomUUID(), UUID.randomUUID(), "problema", null,
                status, LocalDateTime.now(), null, null, ServiceOrderPriority.LOW);
    }

    @Test
    @DisplayName("starts execution and consumes the stock of budget parts")
    void startsExecutionAndConsumesPartStock() {
        UUID serviceOrderId = UUID.randomUUID();
        UUID partId = UUID.randomUUID();
        Budget budget = approvedBudget(serviceOrderId, partId);
        when(serviceOrderRepository.findById(serviceOrderId))
                .thenReturn(Optional.of(order(serviceOrderId, ServiceOrderStatus.AWAITING_APPROVAL)));
        when(budgetRepository.findByServiceOrderId(serviceOrderId)).thenReturn(Optional.of(budget));

        ServiceOrderResponse response = useCase.execute(serviceOrderId);

        assertThat(response.status()).isEqualTo(ServiceOrderStatus.IN_EXECUTION);
        verify(partStockPort, times(1)).decreaseStock(partId, 3);
        verify(serviceOrderRepository, times(1)).save(any());
        verify(domainEventPublisher, times(1)).publishAll(any());
    }

    @Test
    @DisplayName("fails to start execution when the order has no budget")
    void failsWhenThereIsNoBudget() {
        UUID serviceOrderId = UUID.randomUUID();
        when(serviceOrderRepository.findById(serviceOrderId))
                .thenReturn(Optional.of(order(serviceOrderId, ServiceOrderStatus.AWAITING_APPROVAL)));
        when(budgetRepository.findByServiceOrderId(serviceOrderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(serviceOrderId)).isInstanceOf(NotFoundException.class);

        verify(partStockPort, never()).decreaseStock(any(), anyInt());
        verify(serviceOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("starts execution without touching stock when the budget has only services")
    void startsExecutionWithoutParts() {
        UUID serviceOrderId = UUID.randomUUID();
        when(serviceOrderRepository.findById(serviceOrderId))
                .thenReturn(Optional.of(order(serviceOrderId, ServiceOrderStatus.AWAITING_APPROVAL)));
        when(budgetRepository.findByServiceOrderId(serviceOrderId))
                .thenReturn(Optional.of(approvedBudget(serviceOrderId, null)));

        ServiceOrderResponse response = useCase.execute(serviceOrderId);

        assertThat(response.status()).isEqualTo(ServiceOrderStatus.IN_EXECUTION);
        verify(partStockPort, never()).decreaseStock(any(), anyInt());
    }

    @Test
    @DisplayName("fails to start execution while the customer has not approved the budget")
    void failsWhenBudgetNotApproved() {
        UUID serviceOrderId = UUID.randomUUID();
        Budget pending = Budget.create(serviceOrderId);
        pending.addPart(UUID.randomUUID(), "Filtro", 1, new BigDecimal("40.00"));
        pending.finalizeBudget();
        when(serviceOrderRepository.findById(serviceOrderId))
                .thenReturn(Optional.of(order(serviceOrderId, ServiceOrderStatus.AWAITING_APPROVAL)));
        when(budgetRepository.findByServiceOrderId(serviceOrderId)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> useCase.execute(serviceOrderId)).isInstanceOf(ConflictException.class);

        verify(partStockPort, never()).decreaseStock(any(), anyInt());
        verify(serviceOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("fails to start execution when the order is not awaiting approval")
    void failsWhenNotAwaitingApproval() {
        UUID serviceOrderId = UUID.randomUUID();
        when(serviceOrderRepository.findById(serviceOrderId))
                .thenReturn(Optional.of(order(serviceOrderId, ServiceOrderStatus.RECEIVED)));
        when(budgetRepository.findByServiceOrderId(serviceOrderId))
                .thenReturn(Optional.of(approvedBudget(serviceOrderId, UUID.randomUUID())));

        assertThatThrownBy(() -> useCase.execute(serviceOrderId)).isInstanceOf(ConflictException.class);

        verify(partStockPort, never()).decreaseStock(any(), anyInt());
        verify(serviceOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("fails to start execution when the order does not exist")
    void failsWhenOrderNotFound() {
        UUID serviceOrderId = UUID.randomUUID();
        when(serviceOrderRepository.findById(serviceOrderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(serviceOrderId)).isInstanceOf(NotFoundException.class);
    }
}
