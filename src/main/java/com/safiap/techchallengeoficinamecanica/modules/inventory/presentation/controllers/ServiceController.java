package com.safiap.techchallengeoficinamecanica.modules.inventory.presentation.controllers;

import com.safiap.techchallengeoficinamecanica.modules.inventory.application.commands.AlterServiceCommand;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.commands.RegisterServiceCommand;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.responses.service.AlterServiceResponse;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.responses.service.GetServiceResponse;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.responses.service.RegisterServiceResponse;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.use_cases.AlterServiceUseCase;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.use_cases.DeleteServiceUseCase;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.use_cases.GetServiceByIdUseCase;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.use_cases.ListServicesUseCase;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.use_cases.RegisterServiceUseCase;
import com.safiap.techchallengeoficinamecanica.modules.inventory.presentation.dto.service.AlterServiceDTO;
import com.safiap.techchallengeoficinamecanica.modules.inventory.presentation.dto.service.RegisterServiceDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/service")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class ServiceController {

    private final RegisterServiceUseCase registerServiceUseCase;
    private final AlterServiceUseCase alterServiceUseCase;
    private final DeleteServiceUseCase deleteServiceUseCase;
    private final GetServiceByIdUseCase getServiceByIdUseCase;
    private final ListServicesUseCase listServicesUseCase;

    public ServiceController(RegisterServiceUseCase registerServiceUseCase,
                             AlterServiceUseCase alterServiceUseCase,
                             DeleteServiceUseCase deleteServiceUseCase,
                             GetServiceByIdUseCase getServiceByIdUseCase,
                             ListServicesUseCase listServicesUseCase) {
        this.registerServiceUseCase = registerServiceUseCase;
        this.alterServiceUseCase = alterServiceUseCase;
        this.deleteServiceUseCase = deleteServiceUseCase;
        this.getServiceByIdUseCase = getServiceByIdUseCase;
        this.listServicesUseCase = listServicesUseCase;
    }

    @PostMapping
    public ResponseEntity<RegisterServiceResponse> registerService(
            @Valid @RequestBody RegisterServiceDTO request) {
        RegisterServiceCommand command = new RegisterServiceCommand(
                request.name(),
                request.description(),
                request.price()
        );

        RegisterServiceResponse response = registerServiceUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GetServiceResponse> getService(@PathVariable UUID id) {
        return ResponseEntity.ok(getServiceByIdUseCase.execute(id));
    }

    @GetMapping
    public ResponseEntity<List<GetServiceResponse>> listServices() {
        return ResponseEntity.ok(listServicesUseCase.execute());
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlterServiceResponse> alterService(
            @PathVariable UUID id,
            @Valid @RequestBody AlterServiceDTO request) {
        AlterServiceCommand command = new AlterServiceCommand(
                request.name(),
                request.description(),
                request.price()
        );

        return ResponseEntity.ok(alterServiceUseCase.execute(command, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteService(@PathVariable UUID id) {
        deleteServiceUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }
}
