package com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.controllers;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.commands.OpenServiceOrderCommand;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.commands.OpenServiceOrderWithBudgetCommand;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.responses.ServiceOrderResponse;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.responses.ServiceOrderStatusResponse;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.responses.ServiceOrderWithBudgetResponse;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.GetAllServiceOrdersUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.GetServiceOrderByIdUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.GetServiceOrderStatusUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.ListServiceOrdersByCustomerUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.ListServiceOrdersByStatusUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.OpenServiceOrderUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.OpenServiceOrderWithBudgetUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.PullServiceOrderUseCase;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.ServiceOrderStatus;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.dto.BudgetItemMapper;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.dto.OpenServiceOrderDTO;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.dto.OpenServiceOrderWithBudgetDTO;
import com.safiap.techchallengeoficinamecanica.modules.shared.presentation.AuthenticatedCustomer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Abertura e consulta de ordens de servico. O avanco da OS pelo fluxo (mudancas de status e
 * prioridade) fica em {@link ServiceOrderWorkflowController}.
 */
@RestController
@RequestMapping("/service-orders")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class ServiceOrderController {

    private final OpenServiceOrderUseCase openServiceOrderUseCase;
    private final OpenServiceOrderWithBudgetUseCase openServiceOrderWithBudgetUseCase;
    private final GetServiceOrderByIdUseCase getServiceOrderByIdUseCase;
    private final GetServiceOrderStatusUseCase getServiceOrderStatusUseCase;
    private final GetAllServiceOrdersUseCase getAllServiceOrdersUseCase;
    private final ListServiceOrdersByStatusUseCase listServiceOrdersByStatusUseCase;
    private final ListServiceOrdersByCustomerUseCase listServiceOrdersByCustomerUseCase;
    private final PullServiceOrderUseCase pullServiceOrderUseCase;

    public ServiceOrderController(OpenServiceOrderUseCase openServiceOrderUseCase,
                                  OpenServiceOrderWithBudgetUseCase openServiceOrderWithBudgetUseCase,
                                  GetServiceOrderByIdUseCase getServiceOrderByIdUseCase,
                                  GetServiceOrderStatusUseCase getServiceOrderStatusUseCase,
                                  GetAllServiceOrdersUseCase getAllServiceOrdersUseCase,
                                  ListServiceOrdersByStatusUseCase listServiceOrdersByStatusUseCase,
                                  ListServiceOrdersByCustomerUseCase listServiceOrdersByCustomerUseCase,
                                  PullServiceOrderUseCase pullServiceOrderUseCase) {
        this.openServiceOrderUseCase = openServiceOrderUseCase;
        this.openServiceOrderWithBudgetUseCase = openServiceOrderWithBudgetUseCase;
        this.getServiceOrderByIdUseCase = getServiceOrderByIdUseCase;
        this.getServiceOrderStatusUseCase = getServiceOrderStatusUseCase;
        this.getAllServiceOrdersUseCase = getAllServiceOrdersUseCase;
        this.listServiceOrdersByStatusUseCase = listServiceOrdersByStatusUseCase;
        this.listServiceOrdersByCustomerUseCase = listServiceOrdersByCustomerUseCase;
        this.pullServiceOrderUseCase = pullServiceOrderUseCase;
    }

    @PostMapping
    public ResponseEntity<ServiceOrderResponse> openServiceOrder(@Valid @RequestBody OpenServiceOrderDTO request) {
        OpenServiceOrderCommand command = new OpenServiceOrderCommand(
                request.customerId(), request.vehicleId(), request.problemDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(openServiceOrderUseCase.execute(command));
    }

    /** Abertura ja com o orcamento montado, em uma chamada so. */
    @PostMapping("/with-budget")
    public ResponseEntity<ServiceOrderWithBudgetResponse> openServiceOrderWithBudget(
            @Valid @RequestBody OpenServiceOrderWithBudgetDTO request) {
        OpenServiceOrderWithBudgetCommand command = new OpenServiceOrderWithBudgetCommand(
                request.customerId(), request.vehicleId(), request.problemDescription(),
                BudgetItemMapper.toInputs(request.items()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(openServiceOrderWithBudgetUseCase.execute(command));
    }

    /**
     * Listagem da oficina. Sem filtro devolve a fila de trabalho: OS finalizadas e entregues
     * ficam de fora (exclusão lógica — o registro continua no banco e acessível por id), e o
     * resto vem ordenado por status (Em execução > Aguardando aprovação > Diagnóstico >
     * Recebida) e, dentro de cada status, da mais antiga para a mais nova.
     * Com {@code ?status=} devolve todas as OS daquele status, inclusive as já encerradas.
     */
    @GetMapping
    public ResponseEntity<List<ServiceOrderResponse>> listServiceOrders(
            @RequestParam(required = false) ServiceOrderStatus status) {
        return ResponseEntity.ok(status == null
                ? getAllServiceOrdersUseCase.execute()
                : listServiceOrdersByStatusUseCase.execute(status));
    }

    @GetMapping("/{serviceOrderId}")
    public ResponseEntity<ServiceOrderResponse> getById(@PathVariable UUID serviceOrderId) {
        return ResponseEntity.ok(getServiceOrderByIdUseCase.execute(serviceOrderId));
    }

    @GetMapping("/status/{serviceOrderId}")
    public ResponseEntity<ServiceOrderStatusResponse> getServiceOrderStatus(@PathVariable UUID serviceOrderId) {
        return ResponseEntity.ok(getServiceOrderStatusUseCase.execute(serviceOrderId));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<ServiceOrderResponse>> listByCustomer(@PathVariable UUID customerId) {
        return ResponseEntity.ok(listServiceOrdersByCustomerUseCase.execute(customerId));
    }

    /** Proxima OS da fila: maior prioridade e, no empate, a mais antiga. */
    @GetMapping("/pullNext")
    public ResponseEntity<ServiceOrderResponse> pullNext() {
        return ResponseEntity.ok(pullServiceOrderUseCase.execute());
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/my-orders")
    public ResponseEntity<List<ServiceOrderResponse>> getMyServiceOrders(@AuthenticationPrincipal Jwt token) {
        return ResponseEntity.ok(
                listServiceOrdersByCustomerUseCase.execute(AuthenticatedCustomer.requireId(token)));
    }
}
