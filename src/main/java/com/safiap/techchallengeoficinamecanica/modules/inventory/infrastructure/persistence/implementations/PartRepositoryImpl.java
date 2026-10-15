package com.safiap.techchallengeoficinamecanica.modules.inventory.infrastructure.persistence.implementations;

import com.safiap.techchallengeoficinamecanica.modules.inventory.domain.entities.Part;
import com.safiap.techchallengeoficinamecanica.modules.inventory.domain.repositories.PartRepository;
import com.safiap.techchallengeoficinamecanica.modules.inventory.infrastructure.persistence.entities.JpaPartEntity;
import com.safiap.techchallengeoficinamecanica.modules.inventory.infrastructure.persistence.mappers.PartMapper;
import com.safiap.techchallengeoficinamecanica.modules.inventory.infrastructure.persistence.repositories.JpaPartRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PartRepositoryImpl implements PartRepository {

    private final JpaPartRepository jpaPartRepository;

    public PartRepositoryImpl(JpaPartRepository jpaPartRepository) {
        this.jpaPartRepository = jpaPartRepository;
    }

    @Override
    public Optional<Part> save(Part part) {
        JpaPartEntity saved = jpaPartRepository.save(PartMapper.toJPA(part));
        return Optional.of(PartMapper.toEntity(saved));
    }

    @Override
    public void delete(UUID partId) {
        jpaPartRepository.deleteById(partId);
    }

    @Override
    public Optional<Part> findById(UUID id) {
        return jpaPartRepository
                .findById(id)
                .map(PartMapper::toEntity);
    }

    @Override
    public List<Part> findAll() {
        return jpaPartRepository
                .findAll()
                .stream()
                .map(PartMapper::toEntity)
                .toList();
    }
}
