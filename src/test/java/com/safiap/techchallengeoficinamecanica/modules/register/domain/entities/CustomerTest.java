package com.safiap.techchallengeoficinamecanica.modules.register.domain.entities;

import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.CnpjCpf;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.CustomerStatus;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.Email;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.Phone;
import com.safiap.techchallengeoficinamecanica.modules.register.infrastructure.persistence.entities.JPACustomerEntity;
import com.safiap.techchallengeoficinamecanica.modules.register.infrastructure.persistence.mappers.CustomerMapper;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerTest {

    private final Email email = new Email("joao@example.com");
    private final Phone phone = new Phone("11999998888");
    private final CnpjCpf cnpjCpf = new CnpjCpf("111.444.777-35");

    @Test
    @DisplayName("creates a customer generating an id")
    void createsCustomerGeneratingId() {
        Customer customer = Customer.createCustomer("João", email, phone, cnpjCpf);

        assertThat(customer.getCustomerId()).isNotNull();
        assertThat(customer.getName()).isEqualTo("João");
        assertThat(customer.getEmail()).isEqualTo(email);
        assertThat(customer.getPhone()).isEqualTo(phone);
        assertThat(customer.getCnpjCpf()).isEqualTo(cnpjCpf);
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    @DisplayName("fails to create a customer with a blank name")
    void createFailsWhenNameIsBlank() {
        assertThatThrownBy(() -> Customer.createCustomer(" ", email, phone, cnpjCpf))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("fails to create a customer with a null email")
    void createFailsWhenEmailIsNull() {
        assertThatThrownBy(() -> Customer.createCustomer("João", null, phone, cnpjCpf))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("fails to create a customer with a null phone")
    void createFailsWhenPhoneIsNull() {
        assertThatThrownBy(() -> Customer.createCustomer("João", email, null, cnpjCpf))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("fails to create a customer with a null document")
    void createFailsWhenCnpjCpfIsNull() {
        assertThatThrownBy(() -> Customer.createCustomer("João", email, phone, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("rebuilds a customer keeping the provided id")
    void buildsCustomerKeepingProvidedId() {
        UUID customerId = UUID.randomUUID();

        Customer customer = Customer.buildCustomer(customerId, "Maria", email, phone, cnpjCpf);

        assertThat(customer.getCustomerId()).isEqualTo(customerId);
        assertThat(customer.getName()).isEqualTo("Maria");
    }

    @Test
    @DisplayName("rebuilds a customer preserving its status")
    void buildsCustomerKeepingProvidedStatus() {
        Customer customer = Customer.buildCustomer(
                UUID.randomUUID(), "Maria", email, phone, cnpjCpf, CustomerStatus.INACTIVE);

        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
    }

    @Test
    @DisplayName("changes customer status")
    void changesStatus() {
        Customer customer = Customer.createCustomer("João", email, phone, cnpjCpf);

        customer.changeStatus(CustomerStatus.INACTIVE);

        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
    }

    @Test
    @DisplayName("rejects a null customer status")
    void rejectsNullStatus() {
        Customer customer = Customer.createCustomer("João", email, phone, cnpjCpf);

        assertThatThrownBy(() -> customer.changeStatus(null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("persists and restores customer status through its mapper")
    void mapsCustomerStatus() {
        Customer customer = Customer.buildCustomer(
                UUID.randomUUID(), "Maria", email, phone, cnpjCpf, CustomerStatus.INACTIVE);

        JPACustomerEntity persistedCustomer = CustomerMapper.toJPA(customer);
        Customer restoredCustomer = CustomerMapper.toEntity(persistedCustomer);

        assertThat(persistedCustomer.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
        assertThat(restoredCustomer.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
    }

    @Test
    @DisplayName("fails to rebuild a customer with a null id")
    void buildFailsWhenIdIsNull() {
        assertThatThrownBy(() -> Customer.buildCustomer(null, "Maria", email, phone, cnpjCpf))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("alters customer data")
    void altersCustomerData() {
        Customer customer = Customer.createCustomer("João", email, phone, cnpjCpf);
        Email newEmail = new Email("maria@example.com");
        Phone newPhone = new Phone("1133334444");
        CnpjCpf newDocument = new CnpjCpf("11.222.333/0001-81");

        customer.alterCustomer("Maria", newEmail, newPhone, newDocument);

        assertThat(customer.getName()).isEqualTo("Maria");
        assertThat(customer.getEmail()).isEqualTo(newEmail);
        assertThat(customer.getPhone()).isEqualTo(newPhone);
        assertThat(customer.getCnpjCpf()).isEqualTo(newDocument);
    }

    @Test
    @DisplayName("fails to alter a customer with a blank name")
    void alterFailsWhenNameIsBlank() {
        Customer customer = Customer.createCustomer("João", email, phone, cnpjCpf);

        assertThatThrownBy(() -> customer.alterCustomer(" ", email, phone, cnpjCpf))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("fails to alter a customer with a null name")
    void alterFailsWhenNameIsNull() {
        Customer customer = Customer.createCustomer("João", email, phone, cnpjCpf);

        assertThatThrownBy(() -> customer.alterCustomer(null, email, phone, cnpjCpf))
                .isInstanceOf(DomainException.class);
    }
}
