package com.safiap.techchallengeoficinamecanica.modules.register.application;

import com.safiap.techchallengeoficinamecanica.modules.register.application.commands.customer.AlterCustomerCommand;
import com.safiap.techchallengeoficinamecanica.modules.register.application.commands.vehicle.AlterVehicleCommand;
import com.safiap.techchallengeoficinamecanica.modules.register.application.use_cases.customer.AlterCustomerUseCase;
import com.safiap.techchallengeoficinamecanica.modules.register.application.use_cases.customer.GetAllCustomersUseCase;
import com.safiap.techchallengeoficinamecanica.modules.register.application.use_cases.customer.GetCustomerByCnpjCpfUseCase;
import com.safiap.techchallengeoficinamecanica.modules.register.application.use_cases.vehicle.AlterVehicleUseCase;
import com.safiap.techchallengeoficinamecanica.modules.register.application.use_cases.vehicle.GetAllVehiclesByCustomerIdUseCase;
import com.safiap.techchallengeoficinamecanica.modules.register.application.use_cases.vehicle.GetVehicleByCarLicensePlateUseCase;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.entities.Customer;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.entities.Vehicle;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.repositories.CustomerRepository;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.repositories.VehicleRepository;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.CarLicensePlate;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.CnpjCpf;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.Email;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.Kilometers;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.Phone;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.DomainException;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Year;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterUseCasesTest {
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final Customer customer = Customer.createCustomer("Ana", new Email("ana@example.com"),
            new Phone("11999998888"), new CnpjCpf("52998224725"));
    private final Vehicle vehicle = Vehicle.createVehicle(customer.getCustomerId(), new CarLicensePlate("ABC1D23"),
            "Civic", "Honda", new Kilometers(1000), Year.of(2020));

    @Test
    void looksUpCustomersByDocumentAndListsThem() {
        when(customers.findByCnpjCpf(any(CnpjCpf.class))).thenReturn(Optional.of(customer));
        when(customers.findAll()).thenReturn(List.of(customer));
        assertThat(new GetCustomerByCnpjCpfUseCase(customers).execute("52998224725").name()).isEqualTo("Ana");
        assertThat(new GetAllCustomersUseCase(customers).execute()).hasSize(1);
        when(customers.findByCnpjCpf(any(CnpjCpf.class))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new GetCustomerByCnpjCpfUseCase(customers).execute("52998224725"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void altersCustomerAndReportsMissingCustomer() {
        when(customers.findByCustomerId(customer.getCustomerId())).thenReturn(Optional.of(customer));
        var command = new AlterCustomerCommand(customer.getCustomerId(), "Ana Maria", "ana.maria@example.com",
                "11999997777", "52998224725");
        assertThat(new AlterCustomerUseCase(customers).execute(command).name()).isEqualTo("Ana Maria");
        verify(customers).save(customer);
        when(customers.findByCustomerId(customer.getCustomerId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new AlterCustomerUseCase(customers).execute(command))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void looksUpVehiclesByPlateAndCustomer() {
        when(vehicles.findByCarLicensePlate(any(CarLicensePlate.class))).thenReturn(Optional.of(vehicle));
        when(vehicles.findByCustomerId(customer.getCustomerId())).thenReturn(Optional.of(vehicle));
        assertThat(new GetVehicleByCarLicensePlateUseCase(vehicles).execute("ABC1D23").model()).isEqualTo("Civic");
        assertThat(new GetAllVehiclesByCustomerIdUseCase(vehicles).execute(customer.getCustomerId())).hasSize(1);
        when(vehicles.findByCarLicensePlate(any(CarLicensePlate.class))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new GetVehicleByCarLicensePlateUseCase(vehicles).execute("ABC1D23"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void altersVehicleWithoutDecreasingOdometer() {
        when(vehicles.findByVehicleId(vehicle.getVehicleId())).thenReturn(Optional.of(vehicle));
        var command = new AlterVehicleCommand(vehicle.getVehicleId(), customer.getCustomerId(), "XYZ9A99",
                "Corolla", "Toyota", 1500, Year.of(2021));
        assertThat(new AlterVehicleUseCase(vehicles).execute(command).model()).isEqualTo("Corolla");
        verify(vehicles).save(vehicle);
        var decrease = new AlterVehicleCommand(vehicle.getVehicleId(), customer.getCustomerId(), "XYZ9A99",
                "Corolla", "Toyota", 500, Year.of(2021));
        assertThatThrownBy(() -> new AlterVehicleUseCase(vehicles).execute(decrease))
                .isInstanceOf(DomainException.class);
        when(vehicles.findByVehicleId(vehicle.getVehicleId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new AlterVehicleUseCase(vehicles).execute(command))
                .isInstanceOf(NotFoundException.class);
    }
}
