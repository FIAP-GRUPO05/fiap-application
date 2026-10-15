package com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.repositories;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.entities.Budget;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository {
    void save(Budget budget);
    Optional<Budget> findByServiceOrderId(UUID serviceOrderId);
    Optional<Budget> findById(UUID budgetId);
    List<ServiceDuration> findServiceDurations(UUID serviceOrderId);
}
