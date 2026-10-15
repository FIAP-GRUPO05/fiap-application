package com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.commands.ApproveBudgetCommand;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.responses.BudgetResponse;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.entities.Budget;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.repositories.BudgetRepository;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.BudgetStatus;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.ConflictException;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApproveBudgetUseCaseTest {

    private final BudgetRepository budgetRepository = mock(BudgetRepository.class);
    private final ApproveBudgetUseCase useCase = new ApproveBudgetUseCase(budgetRepository);

    private Budget finalizedBudget(UUID serviceOrderId) {
        Budget budget = Budget.create(serviceOrderId);
        budget.addService(UUID.randomUUID(), "Alinhamento", 1, new BigDecimal("200.00"));
        budget.finalizeBudget();
        return budget;
    }

    @Test
    @DisplayName("approves the budget of the service order")
    void approvesBudget() {
        UUID serviceOrderId = UUID.randomUUID();
        Budget budget = finalizedBudget(serviceOrderId);
        when(budgetRepository.findByServiceOrderId(serviceOrderId)).thenReturn(Optional.of(budget));

        BudgetResponse response = useCase.execute(new ApproveBudgetCommand(serviceOrderId));

        assertThat(response.status()).isEqualTo(BudgetStatus.APPROVED);
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.APPROVED);
        verify(budgetRepository, times(1)).save(budget);
    }

    @Test
    @DisplayName("fails when the service order has no budget")
    void failsWhenBudgetNotFound() {
        UUID serviceOrderId = UUID.randomUUID();
        when(budgetRepository.findByServiceOrderId(serviceOrderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ApproveBudgetCommand(serviceOrderId)))
                .isInstanceOf(NotFoundException.class);

        verify(budgetRepository, never()).save(any());
    }

    @Test
    @DisplayName("fails to approve a budget that is still a draft")
    void failsWhenBudgetIsDraft() {
        UUID serviceOrderId = UUID.randomUUID();
        Budget draft = Budget.create(serviceOrderId);
        draft.addService(UUID.randomUUID(), "Alinhamento", 1, new BigDecimal("200.00"));
        when(budgetRepository.findByServiceOrderId(serviceOrderId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> useCase.execute(new ApproveBudgetCommand(serviceOrderId)))
                .isInstanceOf(ConflictException.class);

        verify(budgetRepository, never()).save(any());
    }

    @Test
    @DisplayName("approving twice is idempotent: same answer, no second state change")
    void approvingTwiceIsIdempotent() {
        UUID serviceOrderId = UUID.randomUUID();
        Budget budget = finalizedBudget(serviceOrderId);
        budget.approve();
        when(budgetRepository.findByServiceOrderId(serviceOrderId)).thenReturn(Optional.of(budget));

        BudgetResponse response = useCase.execute(new ApproveBudgetCommand(serviceOrderId));

        assertThat(response.status()).isEqualTo(BudgetStatus.APPROVED);
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.APPROVED);
    }

    @Test
    @DisplayName("a declined budget cannot be approved afterwards")
    void failsWhenAlreadyDeclined() {
        UUID serviceOrderId = UUID.randomUUID();
        Budget budget = finalizedBudget(serviceOrderId);
        budget.decline();
        when(budgetRepository.findByServiceOrderId(serviceOrderId)).thenReturn(Optional.of(budget));

        assertThatThrownBy(() -> useCase.execute(new ApproveBudgetCommand(serviceOrderId)))
                .isInstanceOf(ConflictException.class);
    }
}
