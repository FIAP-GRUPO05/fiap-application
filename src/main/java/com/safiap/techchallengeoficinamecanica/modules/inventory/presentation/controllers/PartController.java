package com.safiap.techchallengeoficinamecanica.modules.inventory.presentation.controllers;

import com.safiap.techchallengeoficinamecanica.modules.inventory.application.commands.AlterPartCommand;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.commands.RegisterPartCommand;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.responses.part.AlterPartResponse;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.responses.part.GetPartResponse;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.responses.part.RegisterPartResponse;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.use_cases.AlterPartUseCase;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.use_cases.DecreasePartStockUseCase;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.use_cases.DeletePartUseCase;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.use_cases.GetPartByIdUseCase;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.use_cases.IncreasePartStockUseCase;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.use_cases.ListPartsUseCase;
import com.safiap.techchallengeoficinamecanica.modules.inventory.application.use_cases.RegisterPartUseCase;
import com.safiap.techchallengeoficinamecanica.modules.inventory.presentation.dto.part.AlterPartDTO;
import com.safiap.techchallengeoficinamecanica.modules.inventory.presentation.dto.part.RegisterPartDTO;
import com.safiap.techchallengeoficinamecanica.modules.inventory.presentation.dto.part.StockMovementDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/part")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class PartController {

    private final RegisterPartUseCase registerPartUseCase;
    private final AlterPartUseCase alterPartUseCase;
    private final DeletePartUseCase deletePartUseCase;
    private final GetPartByIdUseCase getPartByIdUseCase;
    private final ListPartsUseCase listPartsUseCase;
    private final IncreasePartStockUseCase increasePartStockUseCase;
    private final DecreasePartStockUseCase decreasePartStockUseCase;

    public PartController(RegisterPartUseCase registerPartUseCase,
                          AlterPartUseCase alterPartUseCase,
                          DeletePartUseCase deletePartUseCase,
                          GetPartByIdUseCase getPartByIdUseCase,
                          ListPartsUseCase listPartsUseCase,
                          IncreasePartStockUseCase increasePartStockUseCase,
                          DecreasePartStockUseCase decreasePartStockUseCase) {
        this.registerPartUseCase = registerPartUseCase;
        this.alterPartUseCase = alterPartUseCase;
        this.deletePartUseCase = deletePartUseCase;
        this.getPartByIdUseCase = getPartByIdUseCase;
        this.listPartsUseCase = listPartsUseCase;
        this.increasePartStockUseCase = increasePartStockUseCase;
        this.decreasePartStockUseCase = decreasePartStockUseCase;
    }

    @PostMapping
    public ResponseEntity<RegisterPartResponse> registerPart(
            @Valid @RequestBody RegisterPartDTO request) {

        RegisterPartCommand command = new RegisterPartCommand(
                request.name(),
                request.description(),
                request.quantity(),
                request.price()
        );

        RegisterPartResponse response = registerPartUseCase.execute(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GetPartResponse> getPart(@PathVariable UUID id) {
        return ResponseEntity.ok(getPartByIdUseCase.execute(id));
    }

    @GetMapping
    public ResponseEntity<List<GetPartResponse>> listParts() {
        return ResponseEntity.ok(listPartsUseCase.execute());
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlterPartResponse> alterPart(
            @PathVariable UUID id,
            @Valid @RequestBody AlterPartDTO request) {

        AlterPartCommand command = new AlterPartCommand(
                request.name(),
                request.description(),
                request.price()
        );

        return ResponseEntity.ok(alterPartUseCase.execute(command, id));
    }

    @PatchMapping("/{id}/stock/increase")
    public ResponseEntity<AlterPartResponse> increaseStock(
            @PathVariable UUID id,
            @Valid @RequestBody StockMovementDTO request) {

        return ResponseEntity.ok(increasePartStockUseCase.execute(id, request.amount()));
    }

    @PatchMapping("/{id}/stock/decrease")
    public ResponseEntity<AlterPartResponse> decreaseStock(
            @PathVariable UUID id,
            @Valid @RequestBody StockMovementDTO request) {

        return ResponseEntity.ok(decreasePartStockUseCase.execute(id, request.amount()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePart(@PathVariable UUID id) {
        deletePartUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }
}
