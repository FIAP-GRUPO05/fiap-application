package com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.commands.ApproveBudgetCommand;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.responses.BudgetResponse;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.entities.Budget;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.repositories.BudgetRepository;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApproveBudgetUseCase {

    private final BudgetRepository budgetRepository;

    public ApproveBudgetUseCase(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    @Transactional
    public BudgetResponse execute(ApproveBudgetCommand command) {
        Budget budget = budgetRepository.findByServiceOrderId(command.serviceOrderId())
                .orElseThrow(() -> new NotFoundException("Budget not found, service order : " + command.serviceOrderId()));

        budget.approve();
        budgetRepository.save(budget);

        return BudgetResponse.from(budget);
    }
}
