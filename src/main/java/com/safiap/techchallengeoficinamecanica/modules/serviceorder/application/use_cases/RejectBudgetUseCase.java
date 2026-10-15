package com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.responses.ServiceOrderResponse;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.entities.Budget;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.entities.ServiceOrder;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.repositories.BudgetRepository;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.repositories.ServiceOrderRepository;
import com.safiap.techchallengeoficinamecanica.modules.shared.domain.events.DomainEventPublisher;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RejectBudgetUseCase {

    private static final Logger log = LoggerFactory.getLogger(RejectBudgetUseCase.class);

    private final ServiceOrderRepository serviceOrderRepository;
    private final BudgetRepository budgetRepository;
    private final DomainEventPublisher domainEventPublisher;

    public RejectBudgetUseCase(ServiceOrderRepository serviceOrderRepository,
                               DomainEventPublisher domainEventPublisher,
                               BudgetRepository budgetRepository) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.budgetRepository = budgetRepository;
    }

    /** Recusa registrada pela oficina (USER/ADMIN) em nome do cliente. */
    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId) {
        return reject(serviceOrderId, null);
    }

    /** Recusa feita pelo próprio cliente: só pode atingir uma OS que pertença a ele. */
    @Transactional
    public ServiceOrderResponse executeAsCustomer(UUID serviceOrderId, UUID requesterCustomerId) {
        return reject(serviceOrderId, requesterCustomerId);
    }

    private ServiceOrderResponse reject(UUID serviceOrderId, UUID requesterCustomerId) {
        ServiceOrder serviceOrder = serviceOrderRepository.findById(serviceOrderId)
                .orElseThrow(() -> new NotFoundException("Service order not found: " + serviceOrderId));
        Budget budget = budgetRepository.findByServiceOrderId(serviceOrder.getServiceOrderId())
                .orElseThrow(() -> new NotFoundException("Budget not found for service order id: " + serviceOrderId));

        if (requesterCustomerId != null && !requesterCustomerId.equals(serviceOrder.getCustomerId())) {
            log.warn("Customer {} tried to reject the budget of service order {}, which belongs to another customer",
                    requesterCustomerId, serviceOrderId);
            throw new AccessDeniedException("Service order does not belong to the authenticated customer");
        }

        budget.decline();
        budgetRepository.save(budget);
        // O orçamento recusado permanece DECLINED: a OS é encerrada e o histórico do que foi
        // orçado precisa continuar íntegro para consulta.
        serviceOrder.rejectBudget();
        serviceOrderRepository.save(serviceOrder);

        domainEventPublisher.publishAll(serviceOrder.pullDomainEvents());

        log.warn("Budget rejected for service order {} - order canceled", serviceOrderId);

        return ServiceOrderResponse.from(serviceOrder);
    }
}
