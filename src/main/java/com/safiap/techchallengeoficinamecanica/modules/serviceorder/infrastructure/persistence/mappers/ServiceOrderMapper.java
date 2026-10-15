package com.safiap.techchallengeoficinamecanica.modules.serviceorder.infrastructure.persistence.mappers;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.entities.ServiceOrder;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.Diagnosis;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.ServiceOrderPriority;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.infrastructure.persistence.entities.JPAServiceOrderEntity;

public class ServiceOrderMapper {

    public static JPAServiceOrderEntity toJPA(ServiceOrder serviceOrder) {
        return new JPAServiceOrderEntity(
                serviceOrder.getServiceOrderId(),
                serviceOrder.getCustomerId(),
                serviceOrder.getVehicleId(),
                serviceOrder.getProblemDescription(),
                serviceOrder.getDiagnosis() != null ? serviceOrder.getDiagnosis().value() : null,
                serviceOrder.getStatus(),
                serviceOrder.getOpenedAt(),
                serviceOrder.getExecutionStartedAt(),
                serviceOrder.getConcludedAt(),
                serviceOrder.getPriority(),
                serviceOrder.getStatusChangedAt()
        );
    }

    public static ServiceOrder toEntity(JPAServiceOrderEntity entity) {
        return ServiceOrder.build(
                entity.getId(),
                entity.getCustomerId(),
                entity.getVehicleId(),
                entity.getProblemDescription(),
                entity.getDiagnosis() != null ? new Diagnosis(entity.getDiagnosis()) : null,
                entity.getStatus(),
                entity.getOpenedAt(),
                entity.getExecutionStartedAt(),
                entity.getConcludedAt(),
                ServiceOrderPriority.fromValue(entity.getPriority()),
                entity.getStatusChangedAt()
        );
    }
}
