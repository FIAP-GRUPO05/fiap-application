package com.safiap.techchallengeoficinamecanica.modules.register.presentation.controllers;

import com.safiap.techchallengeoficinamecanica.modules.register.application.commands.vehicle.AddVehicleCommand;
import com.safiap.techchallengeoficinamecanica.modules.register.application.commands.vehicle.AlterVehicleCommand;
import com.safiap.techchallengeoficinamecanica.modules.register.application.responses.vehicle.AddVehicleResponse;
import com.safiap.techchallengeoficinamecanica.modules.register.application.responses.vehicle.AlterVehicleResponse;
import com.safiap.techchallengeoficinamecanica.modules.register.application.responses.vehicle.GetVehicleResponse;
import com.safiap.techchallengeoficinamecanica.modules.register.application.use_cases.vehicle.*;
import com.safiap.techchallengeoficinamecanica.modules.register.presentation.dto.vehicle.AddVehicleDTO;
import com.safiap.techchallengeoficinamecanica.modules.register.presentation.dto.vehicle.AlterVehicleDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/vehicles")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class VehicleController {

    private final AddVehicleUseCase addVehicleUseCase;
    private final AlterVehicleUseCase alterVehicleUseCase;
    private final GetAllVehiclesByCustomerIdUseCase getAllVehiclesByCustomerIdUseCase;
    private final GetVehicleByIdUseCase getVehicleByIdUseCase;
    private final GetVehicleByCarLicensePlateUseCase getVehicleByCarLicensePlateUseCase;
    private final DeleteVehicleUseCase deleteVehicleUseCase;

    public VehicleController(
            AddVehicleUseCase addVehicleUseCase,
            AlterVehicleUseCase alterVehicleUseCase,
            GetAllVehiclesByCustomerIdUseCase getAllVehiclesByCustomerIdUseCase,
            GetVehicleByIdUseCase getVehicleByIdUseCase,
            GetVehicleByCarLicensePlateUseCase getVehicleByCarLicensePlateUseCase,
            DeleteVehicleUseCase deleteVehicleUseCase
    ) {
        this.addVehicleUseCase = addVehicleUseCase;
        this.alterVehicleUseCase = alterVehicleUseCase;
        this.getAllVehiclesByCustomerIdUseCase = getAllVehiclesByCustomerIdUseCase;
        this.getVehicleByIdUseCase = getVehicleByIdUseCase;
        this.getVehicleByCarLicensePlateUseCase = getVehicleByCarLicensePlateUseCase;
        this.deleteVehicleUseCase = deleteVehicleUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<AddVehicleResponse> addVehicle(
            @Valid @RequestBody AddVehicleDTO request) {

        AddVehicleCommand command = new AddVehicleCommand(
                request.customerId(),
                request.carLicensePlate(),
                request.model(),
                request.manufacturer(),
                request.kilometers(),
                request.year()
        );

        AddVehicleResponse response = addVehicleUseCase.execute(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    };

    @PutMapping("/alter/{id}")
    public ResponseEntity<AlterVehicleResponse> alterVehicle(
            @PathVariable UUID id,
            @Valid @RequestBody AlterVehicleDTO request) {

        AlterVehicleCommand command = new AlterVehicleCommand(
                id,
                request.customerId(),
                request.carLicensePlate(),
                request.model(),
                request.manufacturer(),
                request.kilometers(),
                request.year()
        );

        return ResponseEntity.status(HttpStatus.OK).body(alterVehicleUseCase.execute(command));

    };

    @GetMapping("/customer/{id}")
    public ResponseEntity<List<GetVehicleResponse>> findAllVehicleByCustomerId(
            @PathVariable UUID id
    ) {
        return ResponseEntity.status(HttpStatus.OK).body(getAllVehiclesByCustomerIdUseCase.execute(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GetVehicleResponse> findVehicleById(
            @PathVariable UUID id
    ) {
        return ResponseEntity.status(HttpStatus.OK).body(getVehicleByIdUseCase.execute(id));
    }

    @GetMapping("/plate/{plate}")
    public ResponseEntity<GetVehicleResponse> findVehicleByCarLicensePlate(
            @PathVariable String plate
    ) {
        return ResponseEntity.status(HttpStatus.OK).body(getVehicleByCarLicensePlateUseCase.execute(plate));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVehicleById(
            @PathVariable UUID id
    ) {
        deleteVehicleUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }
}
