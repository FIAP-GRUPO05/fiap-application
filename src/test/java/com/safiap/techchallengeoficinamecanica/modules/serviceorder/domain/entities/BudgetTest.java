package com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.entities;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.BudgetItemType;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.BudgetStatus;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.ConflictException;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetTest {

    private Budget draft() {
        return Budget.create(UUID.randomUUID());
    }

    @Test
    @DisplayName("creates the budget as DRAFT with no items")
    void createsAsDraftWithNoItems() {
        Budget budget = draft();
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.DRAFT);
        assertThat(budget.getItems()).isEmpty();
        assertThat(budget.calculateTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("calculates the total summing parts and services")
    void calculatesTotalFromPartsAndServices() {
        Budget budget = draft();
        budget.addPart(UUID.randomUUID(), "Pastilha", 2, new BigDecimal("89.90"));
        budget.addService(UUID.randomUUID(), "Mão de obra", 1, new BigDecimal("150.00"));

        assertThat(budget.getItems()).hasSize(2);
        assertThat(budget.getItems().get(0).getType()).isEqualTo(BudgetItemType.PART);
        assertThat(budget.getItems().get(1).getType()).isEqualTo(BudgetItemType.SERVICE);
        assertThat(budget.calculateTotal()).isEqualByComparingTo(new BigDecimal("329.80"));
    }

    @Test
    @DisplayName("fails to finalize a budget with no items")
    void finalizeBudgetFailsWhenEmpty() {
        Budget budget = draft();
        assertThatThrownBy(budget::finalizeBudget)
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("finalizes the budget marking it as FINALIZED")
    void finalizeBudgetMarksAsFinalized() {
        Budget budget = draft();
        budget.addPart(UUID.randomUUID(), "Pastilha", 1, new BigDecimal("89.90"));
        budget.finalizeBudget();
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.FINALIZED);
    }

    @Test
    @DisplayName("prevents modifying or refinalizing an already finalized budget")
    void cannotModifyOrRefinalizeAfterFinalized() {
        Budget budget = draft();
        budget.addPart(UUID.randomUUID(), "Pastilha", 1, new BigDecimal("89.90"));
        budget.finalizeBudget();

        assertThatThrownBy(() -> budget.addPart(UUID.randomUUID(), "Outra", 1, BigDecimal.TEN))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(budget::finalizeBudget)
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("exposes the item list as immutable")
    void itemsListIsUnmodifiable() {
        Budget budget = draft();
        budget.addPart(UUID.randomUUID(), "Pastilha", 1, new BigDecimal("10.00"));
        assertThatThrownBy(() -> budget.getItems().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("completes a service item marking the completion date")
    void completesServiceItem() {
        Budget budget = draft();
        budget.addService(UUID.randomUUID(), "Mão de obra", 1, new BigDecimal("150.00"));
        UUID serviceItemId = budget.getItems().get(0).getBudgetItemId();

        budget.completeServiceItem(serviceItemId);

        assertThat(budget.getItems().get(0).getCompletedAt()).isNotNull();
        assertThat(budget.getItems().get(0).isCompleted()).isTrue();
    }

    @Test
    @DisplayName("fails to complete an item that is not a service")
    void cannotCompleteNonServiceItem() {
        Budget budget = draft();
        budget.addPart(UUID.randomUUID(), "Pastilha", 1, new BigDecimal("89.90"));
        UUID partItemId = budget.getItems().get(0).getBudgetItemId();

        assertThatThrownBy(() -> budget.completeServiceItem(partItemId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("fails to complete an already completed service item")
    void cannotCompleteServiceItemTwice() {
        Budget budget = draft();
        budget.addService(UUID.randomUUID(), "Mão de obra", 1, new BigDecimal("150.00"));
        UUID serviceItemId = budget.getItems().get(0).getBudgetItemId();
        budget.completeServiceItem(serviceItemId);

        assertThatThrownBy(() -> budget.completeServiceItem(serviceItemId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("fails to complete a non-existent item")
    void cannotCompleteUnknownItem() {
        Budget budget = draft();
        budget.addService(UUID.randomUUID(), "Mão de obra", 1, new BigDecimal("150.00"));

        assertThatThrownBy(() -> budget.completeServiceItem(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("considers all services completed only when each service has a completion date")
    void allServiceItemsCompletedReflectsServiceItems() {
        Budget budget = draft();
        budget.addPart(UUID.randomUUID(), "Pastilha", 1, new BigDecimal("89.90"));
        budget.addService(UUID.randomUUID(), "Mão de obra", 1, new BigDecimal("150.00"));

        assertThat(budget.allServiceItemsCompleted()).isFalse();

        UUID serviceItemId = budget.getItems().stream()
                .filter(i -> i.getType() == BudgetItemType.SERVICE)
                .findFirst().orElseThrow().getBudgetItemId();
        budget.completeServiceItem(serviceItemId);

        assertThat(budget.allServiceItemsCompleted()).isTrue();
    }

    @Test
    @DisplayName("considers a parts-only budget vacuously completed")
    void allServiceItemsCompletedIsTrueWhenNoServices() {
        Budget budget = draft();
        budget.addPart(UUID.randomUUID(), "Pastilha", 1, new BigDecimal("89.90"));

        assertThat(budget.allServiceItemsCompleted()).isTrue();
    }
    // --- decisao do cliente sobre o orcamento (aprovar / recusar) ---

    private Budget finalized() {
        Budget budget = draft();
        budget.addPart(UUID.randomUUID(), "Pastilha", 1, new BigDecimal("89.90"));
        budget.finalizeBudget();
        return budget;
    }

    @Test
    @DisplayName("approves a finalized budget marking it as APPROVED")
    void approvesFinalizedBudget() {
        Budget budget = finalized();
        budget.approve();
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.APPROVED);
    }

    @Test
    @DisplayName("declines a finalized budget marking it as DECLINED")
    void declinesFinalizedBudget() {
        Budget budget = finalized();
        budget.decline();
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.DECLINED);
    }

    @Test
    @DisplayName("cannot approve or decline a budget still in DRAFT")
    void cannotDecideOnDraft() {
        assertThatThrownBy(draft()::approve).isInstanceOf(ConflictException.class);
        assertThatThrownBy(draft()::decline).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("repeating the same decision is a no-op: approve and decline are idempotent")
    void repeatingTheSameDecisionIsIdempotent() {
        Budget approved = finalized();
        approved.approve();
        assertThatCode(approved::approve).doesNotThrowAnyException();
        assertThat(approved.getStatus()).isEqualTo(BudgetStatus.APPROVED);

        Budget declined = finalized();
        declined.decline();
        assertThatCode(declined::decline).doesNotThrowAnyException();
        assertThat(declined.getStatus()).isEqualTo(BudgetStatus.DECLINED);
    }

    @Test
    @DisplayName("the customer decision is final: the opposite decision is a conflict")
    void oppositeDecisionIsRejected() {
        Budget approved = finalized();
        approved.approve();
        assertThatThrownBy(approved::decline).isInstanceOf(ConflictException.class);
        assertThat(approved.getStatus()).isEqualTo(BudgetStatus.APPROVED);

        Budget declined = finalized();
        declined.decline();
        assertThatThrownBy(declined::approve).isInstanceOf(ConflictException.class);
        assertThat(declined.getStatus()).isEqualTo(BudgetStatus.DECLINED);
    }

    @Test
    @DisplayName("isDecided reports whether the customer has already answered")
    void isDecidedReportsTheAnswer() {
        assertThat(draft().isDecided()).isFalse();
        assertThat(finalized().isDecided()).isFalse();

        Budget approved = finalized();
        approved.approve();
        assertThat(approved.isDecided()).isTrue();

        Budget declined = finalized();
        declined.decline();
        assertThat(declined.isDecided()).isTrue();
    }

    @Test
    @DisplayName("ensureApproved only lets an APPROVED budget through")
    void ensureApprovedGuard() {
        Budget approved = finalized();
        approved.approve();
        assertThatCode(approved::ensureApproved).doesNotThrowAnyException();

        assertThatThrownBy(finalized()::ensureApproved).isInstanceOf(ConflictException.class);
        assertThatThrownBy(draft()::ensureApproved).isInstanceOf(ConflictException.class);
    }

}
