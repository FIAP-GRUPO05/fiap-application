package com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.controllers;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.commands.ApproveBudgetCommand;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.responses.BudgetResponse;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.responses.ServiceOrderResponse;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.ApproveBudgetUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.GetBudgetUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.GetServiceOrderByIdUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.RejectBudgetUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.BudgetStatus;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.pages.BudgetDecisionPage;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.ConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Decisão do orçamento pelo cliente, aberta direto do e-mail e sem token.
 *
 * <p>O link do e-mail cai no GET, que é <b>seguro</b>: apenas mostra o orçamento. Aprovar e
 * recusar são POST, porque mudam estado — um GET que decide seria disparado por qualquer
 * pré-carregamento de webmail ou antivírus de e-mail, decidindo sozinho pelo cliente.
 *
 * <p>Os dois POST são <b>idempotentes</b>: repetir a mesma decisão devolve 200 com a mesma
 * página e não muda nada (clique duplo, refresh, reenvio do formulário). Só a decisão
 * contrária a uma já registrada é conflito, e nesse caso a resposta é 409 com a página de
 * aviso — nunca uma sobrescrita silenciosa.
 */
@RestController
@RequestMapping("/service-orders/{serviceOrderId}/budget")
@PreAuthorize("permitAll()")
public class BudgetDecisionEmailController {

    private static final String HTML_UTF8 = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8";

    private final ApproveBudgetUseCase approveBudgetUseCase;
    private final RejectBudgetUseCase rejectBudgetUseCase;
    private final GetServiceOrderByIdUseCase getServiceOrderByIdUseCase;
    private final GetBudgetUseCase getBudgetUseCase;

    public BudgetDecisionEmailController(ApproveBudgetUseCase approveBudgetUseCase,
                                         RejectBudgetUseCase rejectBudgetUseCase,
                                         GetServiceOrderByIdUseCase getServiceOrderByIdUseCase,
                                         GetBudgetUseCase getBudgetUseCase) {
        this.approveBudgetUseCase = approveBudgetUseCase;
        this.rejectBudgetUseCase = rejectBudgetUseCase;
        this.getServiceOrderByIdUseCase = getServiceOrderByIdUseCase;
        this.getBudgetUseCase = getBudgetUseCase;
    }

    /**
     * Destino dos links do e-mail. Não decide nada: mostra o orçamento com os dois botões, ou
     * o desfecho quando o cliente já respondeu.
     */
    @GetMapping(value = "/decision", produces = HTML_UTF8)
    public ResponseEntity<String> decisionPage(@PathVariable UUID serviceOrderId,
                                               @RequestParam(required = false) String decision) {
        ServiceOrderResponse serviceOrder = getServiceOrderByIdUseCase.execute(serviceOrderId);
        BudgetResponse budget = getBudgetUseCase.execute(serviceOrderId);

        return html(HttpStatus.OK, pageFor(serviceOrder, budget, decision));
    }

    @PostMapping(value = "/approve", produces = HTML_UTF8)
    public ResponseEntity<String> approve(@PathVariable UUID serviceOrderId) {
        return decide(serviceOrderId, () -> {
            BudgetResponse budget = approveBudgetUseCase.execute(new ApproveBudgetCommand(serviceOrderId));
            return BudgetDecisionPage.approved(getServiceOrderByIdUseCase.execute(serviceOrderId), budget);
        });
    }

    @PostMapping(value = "/reject", produces = HTML_UTF8)
    public ResponseEntity<String> reject(@PathVariable UUID serviceOrderId) {
        return decide(serviceOrderId, () -> {
            ServiceOrderResponse serviceOrder = rejectBudgetUseCase.execute(serviceOrderId);
            return BudgetDecisionPage.rejected(serviceOrder, getBudgetUseCase.execute(serviceOrderId));
        });
    }

    /**
     * O cliente está no navegador: um conflito precisa virar página legível, não o JSON de erro
     * que o restante da API devolve.
     */
    private ResponseEntity<String> decide(UUID serviceOrderId, DecisionAttempt attempt) {
        try {
            return html(HttpStatus.OK, attempt.run());
        } catch (ConflictException e) {
            return html(HttpStatus.CONFLICT, BudgetDecisionPage.conflict(
                    getServiceOrderByIdUseCase.execute(serviceOrderId),
                    getBudgetUseCase.execute(serviceOrderId)));
        }
    }

    private String pageFor(ServiceOrderResponse serviceOrder, BudgetResponse budget, String decision) {
        if (budget.status() == BudgetStatus.APPROVED) {
            return BudgetDecisionPage.approved(serviceOrder, budget);
        }
        if (budget.status() == BudgetStatus.DECLINED) {
            return BudgetDecisionPage.rejected(serviceOrder, budget);
        }
        return BudgetDecisionPage.pending(serviceOrder, budget, decision);
    }

    private ResponseEntity<String> html(HttpStatus status, String body) {
        return ResponseEntity.status(status)
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(body);
    }

    @FunctionalInterface
    private interface DecisionAttempt {
        String run();
    }
}
