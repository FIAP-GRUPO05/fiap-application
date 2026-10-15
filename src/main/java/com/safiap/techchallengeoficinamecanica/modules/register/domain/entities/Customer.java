package com.safiap.techchallengeoficinamecanica.modules.register.domain.entities;

import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.CnpjCpf;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.CustomerStatus;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.Email;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.Phone;
import com.safiap.techchallengeoficinamecanica.modules.shared.common.AggregateRoot;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.DomainException;

import java.util.UUID;

public class Customer extends AggregateRoot {

    private UUID customerId;
    private String name;
    private Email email;
    private Phone phone;
    private CnpjCpf cnpjCpf;
    private CustomerStatus status;

    private Customer() {}

    private Customer(UUID customerId,
                     String name,
                     Email email,
                     Phone phone,
                     CnpjCpf cnpjCpf,
                     CustomerStatus status) {

        this.customerId = customerId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.cnpjCpf = cnpjCpf;
        this.status = status;
    }


    public UUID getCustomerId() {
        return customerId;
    }

    public String getName() {
        return name;
    }

    public Email getEmail() {
        return email;
    }

    public Phone getPhone() {
        return phone;
    }

    public CnpjCpf getCnpjCpf() {
        return cnpjCpf;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public void changeStatus(CustomerStatus status) {
        DomainException.requireNonNull(status, " status is null");
        this.status = status;
    }

    private void changeName (String newName) {
        if (newName == null || newName.isBlank()) {
            throw new DomainException("Name cannot be null or empty.");
        }
        this.name = newName;
    }

    public static Customer createCustomer(
                                  String name,
                                  Email email,
                                  Phone phone,
                                  CnpjCpf cnpjCpf) {

        DomainException.requireNotBlank(name, " name is blank");
        DomainException.requireNonNull(email, " email is null");
        DomainException.requireNonNull(phone, " phone is null");
        DomainException.requireNonNull(cnpjCpf, " cnpjCpf is null");

        return new Customer(
                UUID.randomUUID(),
                name,
                email,
                phone,
                cnpjCpf,
                CustomerStatus.ACTIVE
        );
    }

    public static Customer buildCustomer( UUID customerId,
                                  String name,
                                  Email email,
                                  Phone phone,
                                  CnpjCpf cnpjCpf) {
        return buildCustomer(customerId, name, email, phone, cnpjCpf, CustomerStatus.ACTIVE);
    }

    public static Customer buildCustomer(UUID customerId,
                                         String name,
                                         Email email,
                                         Phone phone,
                                         CnpjCpf cnpjCpf,
                                         CustomerStatus status) {

        DomainException.requireNonNull(customerId, " customerId is null");
        DomainException.requireNotBlank(name, " name is blank");
        DomainException.requireNonNull(email, " email is null");
        DomainException.requireNonNull(phone, " phone is null");
        DomainException.requireNonNull(cnpjCpf, " cnpjCpf is null");
        DomainException.requireNonNull(status, " status is null");

        return new Customer(
                customerId,
                name,
                email,
                phone,
                cnpjCpf,
                status
        );
    }

    public void alterCustomer(String Name, Email Email, Phone Phone, CnpjCpf cnpjCpf) {
        changeName(Name);
        this.email = Email;
        this.phone = Phone;
        this.cnpjCpf = cnpjCpf;
    }


}
