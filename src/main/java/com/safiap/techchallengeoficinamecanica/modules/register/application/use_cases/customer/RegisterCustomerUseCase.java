package com.safiap.techchallengeoficinamecanica.modules.register.application.use_cases.customer;

import com.safiap.techchallengeoficinamecanica.modules.register.application.commands.customer.RegisterCustomerCommand;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.entities.Customer;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.repositories.CustomerRepository;
import com.safiap.techchallengeoficinamecanica.modules.register.application.responses.customer.RegisterCustomerResponse;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.CnpjCpf;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.Email;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.Phone;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterCustomerUseCase {

    private final CustomerRepository customerRepository;

    public RegisterCustomerUseCase(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public RegisterCustomerResponse execute(RegisterCustomerCommand request) {

        Customer customer = Customer.createCustomer(
                request.name(),
                new Email(request.email()),
                new Phone(request.phone()),
                new CnpjCpf(request.cnpjCpf())
        );

        if (customerRepository.findByCnpjCpf(customer.getCnpjCpf()).isPresent()) {
            throw new ConflictException("Customer document already registered.");
        }

        try {
            customerRepository.save(customer);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Customer document already registered.");
        }

        return new RegisterCustomerResponse(customer.getName(), customer.getCustomerId());

    }

}
