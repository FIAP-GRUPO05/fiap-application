package com.safiap.techchallengeoficinamecanica.modules.inventory.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "parts")
@Getter
@NoArgsConstructor
public class JpaPartEntity {

    @Id
    // The domain creates the UUID before mapping this entity.
    private UUID id;
    @Column(nullable = false)
    private String name;
    @Column(length = 150)
    private String description;
    @Column(nullable = false)
    private BigDecimal price;
    @Column(nullable = false)
    private int quantity;

    public JpaPartEntity(UUID id, String name, String description, BigDecimal price, int quantity) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.quantity = quantity;
    }
}
