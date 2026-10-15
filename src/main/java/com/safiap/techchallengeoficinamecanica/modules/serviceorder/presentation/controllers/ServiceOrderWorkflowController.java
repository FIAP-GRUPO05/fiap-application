package com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.controllers;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.commands.FinalizeDiagnosisCommand;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.responses.ServiceOrderResponse;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.DecreaseServiceOrderPriorityUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.DeliverServiceOrderUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.FinalizeDiagnosisUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.FinalizeServiceOrderUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.IncreaseServiceOrderPriorityUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.RejectBudgetUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.StartDiagnosisUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.StartServiceOrderExecutionUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.dto.BudgetItemMapper;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.dto.FinalizeDiagnosisDTO;
import com.safiap.techchallengeoficinamecanica.modules.shared.presentation.AuthenticatedCustomer;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Avanco da ordem de servico pelo fluxo — cada endpoint e uma transicao de estado
 * (Recebida > Diagnostico > Aguardando aprovacao > Execucao > Finalizada > Entregue) ou
 * um ajuste de prioridade na fila. Abertura e consultas ficam em {@link ServiceOrderController}.
 */
@RestController
@RequestMapping("/service-orders/{serviceOrderId}")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class ServiceOrderWorkflowController {

    private final StartDiagnosisUseCase startDiagnosisUseCase;
    private final FinalizeDiagnosisUseCase finalizeDiagnosisUseCase;
    private final StartServiceOrderExecutionUseCase startServiceOrderExecutionUseCase;
    private final RejectBudgetUseCase rejectBudgetUseCase;
    private final FinalizeServiceOrderUseCase finalizeServiceOrderUseCase;
    private final DeliverServiceOrderUseCase deliverServiceOrderUseCase;
    private final IncreaseServiceOrderPriorityUseCase increaseServiceOrderPriorityUseCase;
    private final DecreaseServiceOrderPriorityUseCase decreaseServiceOrderPriorityUseCase;

    public ServiceOrderWorkflowController(StartDiagnosisUseCase startDiagnosisUseCase,
                                          FinalizeDiagnosisUseCase finalizeDiagnosisUseCase,
                                          StartServiceOrderExecutionUseCase startServiceOrderExecutionUseCase,
                                          RejectBudgetUseCase rejectBudgetUseCase,
                                          FinalizeServiceOrderUseCase finalizeServiceOrderUseCase,
                                          DeliverServiceOrderUseCase deliverServiceOrderUseCase,
                                          IncreaseServiceOrderPriorityUseCase increaseServiceOrderPriorityUseCase,
                                          DecreaseServiceOrderPriorityUseCase decreaseServiceOrderPriorityUseCase) {
        this.startDiagnosisUseCase = startDiagnosisUseCase;
        this.finalizeDiagnosisUseCase = finalizeDiagnosisUseCase;
        this.startServiceOrderExecutionUseCase = startServiceOrderExecutionUseCase;
        this.rejectBudgetUseCase = rejectBudgetUseCase;
        this.finalizeServiceOrderUseCase = finalizeServiceOrderUseCase;
        this.deliverServiceOrderUseCase = deliverServiceOrderUseCase;
        this.increaseServiceOrderPriorityUseCase = increaseServiceOrderPriorityUseCase;
        this.decreaseServiceOrderPriorityUseCase = decreaseServiceOrderPriorityUseCase;
    }

    @PatchMapping("/start-diagnosis")
    public ResponseEntity<ServiceOrderResponse> startDiagnosis(@PathVariable UUID serviceOrderId) {
        return ResponseEntity.ok(startDiagnosisUseCase.execute(serviceOrderId));
    }

    @PatchMapping("/finalize-diagnosis")
    public ResponseEntity<ServiceOrderResponse> finalizeDiagnosis(
            @PathVariable UUID serviceOrderId, @Valid @RequestBody FinalizeDiagnosisDTO request) {
        FinalizeDiagnosisCommand command = new FinalizeDiagnosisCommand(
                serviceOrderId, request.diagnosis(), BudgetItemMapper.toInputs(request.items()));
        return ResponseEntity.ok(finalizeDiagnosisUseCase.execute(command));
    }

    @PatchMapping("/execute")
    public ResponseEntity<ServiceOrderResponse> executeOrder(@PathVariable UUID serviceOrderId) {
        return ResponseEntity.ok(startServiceOrderExecutionUseCase.execute(serviceOrderId));
    }

    /**
     * Recusa registrada pela API. A oficina recusa em nome do cliente; o proprio cliente so
     * alcanca a OS que lhe pertence — dai o caminho separado quando o token e de CUSTOMER.
     * A recusa aberta pelo link do e-mail fica em {@link BudgetDecisionEmailController}.
     */
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'CUSTOMER')")
    @PatchMapping("/reject-budget")
    public ResponseEntity<ServiceOrderResponse> rejectBudget(@PathVariable UUID serviceOrderId,
                                                             Authentication authentication) {
        if (!AuthenticatedCustomer.isCustomer(authentication)) {
            return ResponseEntity.ok(rejectBudgetUseCase.execute(serviceOrderId));
        }
        return ResponseEntity.ok(rejectBudgetUseCase.executeAsCustomer(
                serviceOrderId, AuthenticatedCustomer.requireId(authentication)));
    }

    @PatchMapping("/finalize")
    public ResponseEntity<ServiceOrderResponse> finalizeOrder(@PathVariable UUID serviceOrderId) {
        return ResponseEntity.ok(finalizeServiceOrderUseCase.execute(serviceOrderId));
    }

    @PatchMapping("/deliver")
    public ResponseEntity<ServiceOrderResponse> deliver(@PathVariable UUID serviceOrderId) {
        return ResponseEntity.ok(deliverServiceOrderUseCase.execute(serviceOrderId));
    }

    @PatchMapping("/priority/increase")
    public ResponseEntity<ServiceOrderResponse> increasePriority(@PathVariable UUID serviceOrderId) {
        return ResponseEntity.ok(increaseServiceOrderPriorityUseCase.execute(serviceOrderId));
    }

    @PatchMapping("/priority/decrease")
    public ResponseEntity<ServiceOrderResponse> decreasePriority(@PathVariable UUID serviceOrderId) {
        return ResponseEntity.ok(decreaseServiceOrderPriorityUseCase.execute(serviceOrderId));
    }
}
