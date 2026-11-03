package com.safiap.techchallengeoficinamecanica.modules.register.application.use_cases.customer;

import com.safiap.techchallengeoficinamecanica.modules.register.application.responses.customer.CustomerStatusResponse;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.entities.Customer;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.repositories.CustomerRepository;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.CustomerStatus;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ChangeCustomerStatusUseCase {

    private final CustomerRepository customerRepository;

    public ChangeCustomerStatusUseCase(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerStatusResponse execute(UUID id, CustomerStatus status) {
        Customer customer = customerRepository.findByCustomerId(id)
                .orElseThrow(() -> new NotFoundException("Customer with id " + id + " not found."));

        if (customer.getStatus() != status) {
            customer.changeStatus(status);
            customerRepository.save(customer);
        }

        return new CustomerStatusResponse(customer.getCustomerId(), customer.getName(), customer.getStatus());
    }
}
