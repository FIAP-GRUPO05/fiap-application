package com.safiap.techchallengeoficinamecanica.modules.register.infrastructure.persistence.entities;

import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.CustomerStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "customers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_customers_cnpj_cpf", columnNames = "cnpj_cpf")
})
@Getter
@NoArgsConstructor
public class JPACustomerEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String email;
    @Column(nullable = false)
    private String phone;
    @Column(nullable = false)
    private String cnpjCpf;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CustomerStatus status;

    public JPACustomerEntity(String name, String email, String phone, String cnpjCpf) {
        this(name, email, phone, cnpjCpf, CustomerStatus.ACTIVE);
    }

    public JPACustomerEntity(String name, String email, String phone, String cnpjCpf, CustomerStatus status) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.cnpjCpf = cnpjCpf;
        this.status = status;
    }

    public JPACustomerEntity(UUID customerId, String name, String email, String phone, String cnpjCpf) {
        this(customerId, name, email, phone, cnpjCpf, CustomerStatus.ACTIVE);
    }

    public JPACustomerEntity(UUID customerId, String name, String email, String phone, String cnpjCpf,
                            CustomerStatus status) {
        this.id = customerId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.cnpjCpf = cnpjCpf;
        this.status = status;
    }
}
