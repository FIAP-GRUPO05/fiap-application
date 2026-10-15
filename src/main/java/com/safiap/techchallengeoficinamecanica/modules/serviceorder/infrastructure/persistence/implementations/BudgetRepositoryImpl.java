package com.safiap.techchallengeoficinamecanica.modules.serviceorder.infrastructure.persistence.implementations;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.entities.Budget;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.repositories.BudgetRepository;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.repositories.ServiceDuration;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.BudgetItemType;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.infrastructure.persistence.mappers.BudgetMapper;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.infrastructure.persistence.repositories.JPABudgetItemRepository;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.infrastructure.persistence.repositories.JPABudgetRepository;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BudgetRepositoryImpl implements BudgetRepository {

    private final JPABudgetRepository jpaBudgetRepository;
    private final JPABudgetItemRepository jpaBudgetItemRepository;

    public BudgetRepositoryImpl(JPABudgetRepository jpaBudgetRepository,
                                JPABudgetItemRepository jpaBudgetItemRepository) {
        this.jpaBudgetRepository = jpaBudgetRepository;
        this.jpaBudgetItemRepository = jpaBudgetItemRepository;
    }

    @Override
    public void save(Budget budget) {
        jpaBudgetRepository.save(BudgetMapper.toJPA(budget));
        jpaBudgetItemRepository.deleteByBudgetId(budget.getBudgetId());
        budget.getItems().forEach(item -> jpaBudgetItemRepository.save(BudgetMapper.itemToJPA(item)));
    }

    @Override
    public Optional<Budget> findByServiceOrderId(UUID serviceOrderId) {
        return jpaBudgetRepository.findByServiceOrderId(serviceOrderId)
                .map(entity -> BudgetMapper.toEntity(entity,
                        jpaBudgetItemRepository.findByBudgetId(entity.getId())));
    }

    @Override
    public Optional<Budget> findById(UUID budgetId) {
        return jpaBudgetRepository.findById(budgetId)
                .map(entity -> BudgetMapper.toEntity(entity,
                        jpaBudgetItemRepository.findByBudgetId(entity.getId())));
    }

    @Override
    public List<ServiceDuration> findServiceDurations(UUID serviceOrderId) {
        return jpaBudgetItemRepository.findCompletedServiceTimes(serviceOrderId, BudgetItemType.SERVICE).stream()
                .map(row -> new ServiceDuration(
                        row.getServiceId(),
                        Duration.between(row.getStartedAt(), row.getCompletedAt()).getSeconds()))
                .toList();
    }
}
