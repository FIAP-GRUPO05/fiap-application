package com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.entities;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.BudgetItemType;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.BudgetStatus;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.ConflictException;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.NotFoundException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Budget {

    private UUID budgetId;
    private UUID serviceOrderId;
    private BudgetStatus status;
    private List<BudgetItem> items;
    private LocalDateTime createdAt;

    private Budget(UUID budgetId, UUID serviceOrderId, BudgetStatus status,
                   List<BudgetItem> items, LocalDateTime createdAt) {
        this.budgetId = budgetId;
        this.serviceOrderId = serviceOrderId;
        this.status = status;
        this.items = items;
        this.createdAt = createdAt;
    }

    public static Budget create(UUID serviceOrderId) {
        return new Budget(UUID.randomUUID(), serviceOrderId, BudgetStatus.DRAFT, new ArrayList<>(), LocalDateTime.now());
    }

    public static Budget build(UUID budgetId, UUID serviceOrderId, BudgetStatus status,
                               List<BudgetItem> items, LocalDateTime createdAt) {
        return new Budget(budgetId, serviceOrderId, status, new ArrayList<>(items), createdAt);
    }

    public void addPart(UUID itemId, String description, int quantity, BigDecimal unitPrice) {
        if (this.status == BudgetStatus.FINALIZED)
            throw new ConflictException("Cannot modify a finalized budget");
        items.add(BudgetItem.create(this.budgetId, BudgetItemType.PART, itemId, description, quantity, unitPrice));
    }

    public void addService(UUID itemId, String description, int quantity, BigDecimal unitPrice) {
        if (this.status == BudgetStatus.FINALIZED)
            throw new ConflictException("Cannot modify a finalized budget");
        items.add(BudgetItem.create(this.budgetId, BudgetItemType.SERVICE, itemId, description, quantity, unitPrice));
    }

    public void completeServiceItem(UUID budgetItemId) {
        BudgetItem item = items.stream()
                .filter(i -> i.getBudgetItemId().equals(budgetItemId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Budget item not found: " + budgetItemId));
        item.complete();
    }

    public boolean allServiceItemsCompleted() {
        return items.stream()
                .filter(BudgetItem::isService)
                .allMatch(BudgetItem::isCompleted);
    }

    public BigDecimal calculateTotal() {
        return items.stream()
                .map(BudgetItem::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void finalizeBudget() {
        if (this.status == BudgetStatus.FINALIZED)
            throw new ConflictException("Budget is already finalized");
        if (items.isEmpty())
            throw new ConflictException("Cannot finalize a budget with no items");
        this.status = BudgetStatus.FINALIZED;
    }


    public void approve() {
        if (this.status == BudgetStatus.APPROVED)
            return;
        if (this.status == BudgetStatus.DECLINED)
            throw new ConflictException("Budget was already declined by the customer");
        requireFinalized();
        this.status = BudgetStatus.APPROVED;
    }


    public void decline() {
        if (this.status == BudgetStatus.DECLINED)
            return;
        if (this.status == BudgetStatus.APPROVED)
            throw new ConflictException("Budget was already approved by the customer");
        requireFinalized();
        this.status = BudgetStatus.DECLINED;
    }


    private void requireFinalized() {
        if (this.status != BudgetStatus.FINALIZED)
            throw new ConflictException("Budget must be finalized before the customer decides on it");
    }


    public boolean isDecided() {
        return this.status == BudgetStatus.APPROVED || this.status == BudgetStatus.DECLINED;
    }

    /** Guarda: interrompe a operacao se o cliente ainda nao aprovou o orcamento. */
    public void ensureApproved() {
        if (this.status != BudgetStatus.APPROVED)
            throw new ConflictException("Budget must be approved by customer");
    }

    public UUID getBudgetId() { return budgetId; }
    public UUID getServiceOrderId() { return serviceOrderId; }
    public BudgetStatus getStatus() { return status; }
    public List<BudgetItem> getItems() { return Collections.unmodifiableList(items); }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
