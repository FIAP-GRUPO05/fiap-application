package com.safiap.techchallengeoficinamecanica.modules.register.infrastructure.persistence.mappers;

import com.safiap.techchallengeoficinamecanica.modules.register.domain.entities.Customer;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.CnpjCpf;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.Email;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.Phone;
import com.safiap.techchallengeoficinamecanica.modules.register.infrastructure.persistence.entities.JPACustomerEntity;

public class CustomerMapper {

    public static JPACustomerEntity toJPA(Customer customer) {
        return new JPACustomerEntity(
                customer.getCustomerId(),
                customer.getName(),
                customer.getEmail().value(),
                customer.getPhone().value(),
                customer.getCnpjCpf().value(),
                customer.getStatus()
        );
    }

    public static Customer toEntity(JPACustomerEntity customerEntity) {
        return Customer.buildCustomer(
                customerEntity.getId(),
                customerEntity.getName(),
                new Email(customerEntity.getEmail()),
                new Phone(customerEntity.getPhone()),
                new CnpjCpf(customerEntity.getCnpjCpf()),
                customerEntity.getStatus()
        );
    }
}
