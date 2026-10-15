package com.safiap.techchallengeoficinamecanica.modules.register.presentation.controllers;


import com.safiap.techchallengeoficinamecanica.modules.register.application.commands.customer.RegisterCustomerCommand;
import com.safiap.techchallengeoficinamecanica.modules.register.application.commands.customer.AlterCustomerCommand;
import com.safiap.techchallengeoficinamecanica.modules.register.application.responses.customer.GetCustomerResponse;
import com.safiap.techchallengeoficinamecanica.modules.register.application.responses.customer.RegisterCustomerResponse;
import com.safiap.techchallengeoficinamecanica.modules.register.application.responses.customer.AlterCustomerResponse;
import com.safiap.techchallengeoficinamecanica.modules.register.application.use_cases.customer.*;
import com.safiap.techchallengeoficinamecanica.modules.register.presentation.dto.customer.RegisterCustomerDTO;
import com.safiap.techchallengeoficinamecanica.modules.register.presentation.dto.customer.AlterCustomerDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/customers")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class CustomerController {

    private final RegisterCustomerUseCase registerCustomerUseCase;
    private final AlterCustomerUseCase alterCustomerUseCase;
    private final GetAllCustomersUseCase getAllCustomersUseCase;
    private final GetCustomerByIdUseCase  getCustomerByIdUseCase;
    private final GetCustomerByCnpjCpfUseCase getCustomerByCnpjCpfUseCase;
    private final DeleteCustomerUseCase deleteCustomerUseCase;

    public CustomerController(RegisterCustomerUseCase registerCustomerUseCase,
                              AlterCustomerUseCase alterCustomerUseCase,
                              GetAllCustomersUseCase getAllCustomersUseCase,
                              GetCustomerByIdUseCase getCustomerByIdUseCase,
                              GetCustomerByCnpjCpfUseCase getCustomerByCnpjCpfUseCase,
                              DeleteCustomerUseCase deleteCustomerUseCase) {
        this.registerCustomerUseCase = registerCustomerUseCase;
        this.alterCustomerUseCase = alterCustomerUseCase;
        this.getAllCustomersUseCase = getAllCustomersUseCase;
        this.getCustomerByIdUseCase = getCustomerByIdUseCase;
        this.getCustomerByCnpjCpfUseCase = getCustomerByCnpjCpfUseCase;
        this.deleteCustomerUseCase = deleteCustomerUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterCustomerResponse> registerCustomer(
            @Valid @RequestBody RegisterCustomerDTO request) {

        RegisterCustomerCommand command = new RegisterCustomerCommand(
                request.name(),
                request.email(),
                request.phone(),
                request.cnpjCpf()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(registerCustomerUseCase.execute(command));

    };

    @PutMapping("/alter/{id}")
    public ResponseEntity<AlterCustomerResponse> alterCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody AlterCustomerDTO request) {

        AlterCustomerCommand command = new AlterCustomerCommand(
                id,
                request.name(),
                request.email(),
                request.phone(),
                request.cnpjCpf()
        );

        return ResponseEntity.status(HttpStatus.OK).body(alterCustomerUseCase.execute(command));

    };

    @GetMapping()
    public ResponseEntity<List<GetCustomerResponse>> findAllCustomer() {
        return ResponseEntity.status(HttpStatus.OK).body(getAllCustomersUseCase.execute());
    }

    @GetMapping("/{id}")
    public ResponseEntity<GetCustomerResponse> findCustomerById(
            @PathVariable UUID id
    ) {
        return ResponseEntity.status(HttpStatus.OK).body(getCustomerByIdUseCase.execute(id));
    }

    @GetMapping("/cnpj-cpf/{cnpjCpf}")
    public ResponseEntity<GetCustomerResponse> findCustomerByCnpjCpf(
            @PathVariable String cnpjCpf
    ) {
        return ResponseEntity.status(HttpStatus.OK).body(getCustomerByCnpjCpfUseCase.execute(cnpjCpf));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomerById(
            @PathVariable UUID id
    ) {
        deleteCustomerUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }


}
