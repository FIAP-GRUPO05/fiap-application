package com.safiap.techchallengeoficinamecanica.modules.register.application.use_cases.customer;

import com.safiap.techchallengeoficinamecanica.modules.register.domain.entities.Customer;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.repositories.CustomerRepository;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.CnpjCpf;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.CustomerStatus;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.Email;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.Phone;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangeCustomerStatusUseCaseTest {

    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final ChangeCustomerStatusUseCase useCase = new ChangeCustomerStatusUseCase(customers);

    private Customer customer(CustomerStatus status) {
        return Customer.buildCustomer(UUID.randomUUID(), "Ana", new Email("ana@example.com"),
                new Phone("11999998888"), new CnpjCpf("52998224725"), status);
    }

    @Test
    @DisplayName("deactivates an active customer and persists the change")
    void deactivatesActiveCustomer() {
        Customer customer = customer(CustomerStatus.ACTIVE);
        when(customers.findByCustomerId(customer.getCustomerId())).thenReturn(Optional.of(customer));

        var response = useCase.execute(customer.getCustomerId(), CustomerStatus.INACTIVE);

        assertThat(response.id()).isEqualTo(customer.getCustomerId());
        assertThat(response.name()).isEqualTo("Ana");
        assertThat(response.status()).isEqualTo(CustomerStatus.INACTIVE);
        verify(customers).save(customer);
    }

    @Test
    @DisplayName("activates an inactive customer and persists the change")
    void activatesInactiveCustomer() {
        Customer customer = customer(CustomerStatus.INACTIVE);
        when(customers.findByCustomerId(customer.getCustomerId())).thenReturn(Optional.of(customer));

        var response = useCase.execute(customer.getCustomerId(), CustomerStatus.ACTIVE);

        assertThat(response.status()).isEqualTo(CustomerStatus.ACTIVE);
        verify(customers).save(customer);
    }

    @Test
    @DisplayName("is idempotent when the customer already has the requested status")
    void keepsStatusWithoutSaving() {
        Customer customer = customer(CustomerStatus.INACTIVE);
        when(customers.findByCustomerId(customer.getCustomerId())).thenReturn(Optional.of(customer));

        var response = useCase.execute(customer.getCustomerId(), CustomerStatus.INACTIVE);

        assertThat(response.status()).isEqualTo(CustomerStatus.INACTIVE);
        verify(customers, never()).save(any());
    }

    @Test
    @DisplayName("fails with not found for an unknown customer")
    void failsForUnknownCustomer() {
        UUID id = UUID.randomUUID();
        when(customers.findByCustomerId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, CustomerStatus.INACTIVE))
                .isInstanceOf(NotFoundException.class);
        verify(customers, never()).save(any());
    }
}
