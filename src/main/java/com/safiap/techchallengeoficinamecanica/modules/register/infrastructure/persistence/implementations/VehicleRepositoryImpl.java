package com.safiap.techchallengeoficinamecanica.modules.register.infrastructure.persistence.implementations;

import com.safiap.techchallengeoficinamecanica.modules.register.domain.entities.Vehicle;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.repositories.VehicleRepository;
import com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects.CarLicensePlate;
import com.safiap.techchallengeoficinamecanica.modules.register.infrastructure.persistence.mappers.VehicleMapper;
import com.safiap.techchallengeoficinamecanica.modules.register.infrastructure.persistence.repositories.JPAVehicleRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class VehicleRepositoryImpl implements VehicleRepository {

    private final JPAVehicleRepository jpaVehicleRepository;

    public VehicleRepositoryImpl(JPAVehicleRepository jpaVehicleRepository) {
        this.jpaVehicleRepository = jpaVehicleRepository;
    }

    @Override
    public void save(Vehicle vehicle) {
        jpaVehicleRepository.save(VehicleMapper.toJPA(vehicle));
    }

    @Override
    public Optional<Vehicle> findByVehicleId(UUID vehicleId) {
        return jpaVehicleRepository
                .findById(vehicleId)
                .map(VehicleMapper::toEntity);
    }

    @Override
    public Optional<Vehicle> findByCarLicensePlate(CarLicensePlate carLicensePlate) {
        return jpaVehicleRepository
                .findByCarLicensePlate(carLicensePlate.plate())
                .map(VehicleMapper::toEntity);
    }

    @Override
    public Optional<Vehicle> findByCustomerId(UUID customerId) {
        return jpaVehicleRepository
                .findByCustomerId(customerId)
                .map(VehicleMapper::toEntity);
    }

    @Override
    public void delete(Vehicle vehicle) {
        jpaVehicleRepository.delete(VehicleMapper.toJPA(vehicle));
    }

    @Override
    public List<Vehicle> findAll() {
        return jpaVehicleRepository.findAll().stream().map(VehicleMapper::toEntity ).toList();
    }


}
