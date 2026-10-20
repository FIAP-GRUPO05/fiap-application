package com.safiap.techchallengeoficinamecanica.modules.inventory.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "services")
@Getter
@NoArgsConstructor
public class JpaServiceEntity {

    @Id
    // The domain creates the UUID before mapping this entity.
    private UUID id;
    @Column(nullable = false)
    private String name;
    @Column(length = 150)
    private String description;
    @Column(nullable = false)
    private BigDecimal price;

    public JpaServiceEntity(UUID id, String name, String description, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
    }
}
