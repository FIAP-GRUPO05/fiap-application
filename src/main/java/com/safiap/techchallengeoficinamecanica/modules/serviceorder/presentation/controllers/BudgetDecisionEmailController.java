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
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.security.BudgetAccess;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.ConflictException;
import com.safiap.techchallengeoficinamecanica.modules.shared.presentation.AuthenticatedCustomer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * O link do email abre somente a identificação por CPF; os detalhes e decisões exigem JWT.
 *
 * <p>O link do e-mail cai no GET público de identificação. O orçamento só é exibido após autenticação. Aprovar e
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
@PreAuthorize("hasAnyRole('USER', 'ADMIN', 'CUSTOMER')")
public class BudgetDecisionEmailController {

    private static final String HTML_UTF8 = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8";
    private static final SecureRandom NONCES = new SecureRandom();
    private final String accessTemplate;
    private final String accessScript;

    private final ApproveBudgetUseCase approveBudgetUseCase;
    private final RejectBudgetUseCase rejectBudgetUseCase;
    private final GetServiceOrderByIdUseCase getServiceOrderByIdUseCase;
    private final GetBudgetUseCase getBudgetUseCase;
    private final BudgetAccess budgetAccess;

    public BudgetDecisionEmailController(ApproveBudgetUseCase approveBudgetUseCase,
                                         RejectBudgetUseCase rejectBudgetUseCase,
                                         GetServiceOrderByIdUseCase getServiceOrderByIdUseCase,
                                         GetBudgetUseCase getBudgetUseCase,
                                         BudgetAccess budgetAccess) throws IOException {
        this.accessTemplate = new ClassPathResource("static/budget-access.html").getContentAsString(StandardCharsets.UTF_8);
        this.accessScript = new ClassPathResource("static/budget-access.js").getContentAsString(StandardCharsets.UTF_8);
        this.approveBudgetUseCase = approveBudgetUseCase;
        this.rejectBudgetUseCase = rejectBudgetUseCase;
        this.getServiceOrderByIdUseCase = getServiceOrderByIdUseCase;
        this.getBudgetUseCase = getBudgetUseCase;
        this.budgetAccess = budgetAccess;
    }

    /**
     * Página genérica, sem consultas ao banco ou dados de OS. CPF é enviado somente por POST.
     */
    @PreAuthorize("permitAll()")
    @GetMapping(value = "/decision", produces = HTML_UTF8)
    public ResponseEntity<String> decisionPage(@PathVariable UUID serviceOrderId) {
        byte[] random = new byte[18];
        NONCES.nextBytes(random);
        String nonce = Base64.getEncoder().encodeToString(random);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("Content-Security-Policy", "default-src 'self'; script-src 'nonce-" + nonce + "'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
                .body(accessTemplate.replace("__ACCESS_SCRIPT__", accessScript).replace("__SCRIPT_NONCE__", nonce));
    }

    @GetMapping(value = "/decision/view", produces = HTML_UTF8)
    public ResponseEntity<String> budgetPage(@PathVariable UUID serviceOrderId,
                                            @RequestParam(required = false) String decision,
                                            Authentication authentication) {
        ServiceOrderResponse serviceOrder = budgetAccess.require(serviceOrderId, authentication);
        BudgetResponse budget = getBudgetUseCase.execute(serviceOrderId);

        return html(HttpStatus.OK, pageFor(serviceOrder, budget, decision));
    }

    @PostMapping(value = "/approve", produces = HTML_UTF8)
    public ResponseEntity<String> approve(@PathVariable UUID serviceOrderId, Authentication authentication) {
        budgetAccess.require(serviceOrderId, authentication);
        return decide(serviceOrderId, () -> {
            BudgetResponse budget = approveBudgetUseCase.execute(new ApproveBudgetCommand(serviceOrderId));
            return BudgetDecisionPage.approved(getServiceOrderByIdUseCase.execute(serviceOrderId), budget);
        });
    }

    @PostMapping(value = "/reject", produces = HTML_UTF8)
    public ResponseEntity<String> reject(@PathVariable UUID serviceOrderId, Authentication authentication) {
        budgetAccess.require(serviceOrderId, authentication);
        return decide(serviceOrderId, () -> {
            ServiceOrderResponse serviceOrder = AuthenticatedCustomer.isCustomer(authentication)
                    ? rejectBudgetUseCase.executeAsCustomer(serviceOrderId, AuthenticatedCustomer.requireId(authentication))
                    : rejectBudgetUseCase.execute(serviceOrderId);
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
                .cacheControl(CacheControl.noStore())
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(body);
    }

    @FunctionalInterface
    private interface DecisionAttempt {
        String run();
    }
}
