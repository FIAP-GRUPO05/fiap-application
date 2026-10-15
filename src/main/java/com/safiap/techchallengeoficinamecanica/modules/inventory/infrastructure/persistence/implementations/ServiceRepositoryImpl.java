package com.safiap.techchallengeoficinamecanica.modules.inventory.infrastructure.persistence.implementations;

import com.safiap.techchallengeoficinamecanica.modules.inventory.domain.entities.Service;
import com.safiap.techchallengeoficinamecanica.modules.inventory.domain.repositories.ServiceRepository;
import com.safiap.techchallengeoficinamecanica.modules.inventory.infrastructure.persistence.entities.JpaServiceEntity;
import com.safiap.techchallengeoficinamecanica.modules.inventory.infrastructure.persistence.mappers.ServiceMapper;
import com.safiap.techchallengeoficinamecanica.modules.inventory.infrastructure.persistence.repositories.JpaServiceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ServiceRepositoryImpl implements ServiceRepository {

    private final JpaServiceRepository jpaServiceRepository;

    public ServiceRepositoryImpl(JpaServiceRepository jpaServiceRepository) {
        this.jpaServiceRepository = jpaServiceRepository;
    }

    @Override
    public  Optional<Service> save(Service service) {
        JpaServiceEntity jpaEntity = jpaServiceRepository.save(ServiceMapper.toJPA(service));
        return Optional.of(ServiceMapper.toEntity(jpaEntity));
    }

    @Override
    public void delete(UUID id) {
        jpaServiceRepository.deleteById(id);
    }

    @Override
    public Optional<Service> findById(UUID id) {
        return jpaServiceRepository
                .findById(id)
                .map(ServiceMapper::toEntity);
    }

    @Override
    public List<Service> findAll() {
        return jpaServiceRepository
                .findAll()
                .stream()
                .map(ServiceMapper::toEntity)
                .toList();
    }
}
