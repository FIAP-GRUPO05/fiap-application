package com.safiap.techchallengeoficinamecanica.integration;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.ServiceOrderPriority;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.ServiceOrderStatus;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.infrastructure.persistence.entities.JPAServiceOrderEntity;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.infrastructure.persistence.repositories.JPAServiceOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A regra da listagem (prioridade por status, mais antigas primeiro e exclusão lógica das OS
 * finalizadas e entregues) mora inteira no SQL nativo de
 * {@link JPAServiceOrderRepository#getAllServiceOrdersFiltered()}. Testar o caso de uso com o
 * repositório mockado não prova nada sobre ela: só um teste contra o banco cobre essa regra.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ServiceOrderListingIntegrationTest {

    private static final LocalDateTime REFERENCE = LocalDateTime.of(2026, 1, 10, 8, 0);

    @Autowired
    private JPAServiceOrderRepository repository;

    @Test
    @DisplayName("orders by status priority and, inside each status, from the oldest to the newest")
    void ordersByStatusPriorityThenByAge() {
        UUID inExecutionNew = givenOrder(ServiceOrderStatus.IN_EXECUTION, REFERENCE.plusDays(5));
        UUID received = givenOrder(ServiceOrderStatus.RECEIVED, REFERENCE.plusDays(1));
        UUID inDiagnosis = givenOrder(ServiceOrderStatus.IN_DIAGNOSIS, REFERENCE.plusDays(2));
        UUID inExecutionOld = givenOrder(ServiceOrderStatus.IN_EXECUTION, REFERENCE);
        UUID awaitingApproval = givenOrder(ServiceOrderStatus.AWAITING_APPROVAL, REFERENCE.plusDays(3));
        repository.flush();

        assertThat(listedIdsAmong(inExecutionNew, received, inDiagnosis, inExecutionOld, awaitingApproval))
                .containsExactly(
                        inExecutionOld,     // Em execução, a mais antiga das duas
                        inExecutionNew,
                        awaitingApproval,   // Aguardando aprovação
                        inDiagnosis,        // Diagnóstico
                        received);          // Recebida
    }

    @Test
    @DisplayName("leaves finalized and delivered orders out of the listing without deleting them")
    void excludesFinalizedAndDeliveredKeepingTheRecord() {
        UUID open = givenOrder(ServiceOrderStatus.RECEIVED, REFERENCE);
        UUID finalized = givenOrder(ServiceOrderStatus.FINALIZED, REFERENCE);
        UUID delivered = givenOrder(ServiceOrderStatus.DELIVERED, REFERENCE);
        repository.flush();

        assertThat(listedIdsAmong(open, finalized, delivered)).containsExactly(open);

        // exclusão lógica: fora da listagem, mas o registro continua no banco e acessível por id
        assertThat(repository.findById(finalized)).isPresent();
        assertThat(repository.findById(delivered)).isPresent();
    }

    @Test
    @DisplayName("keeps canceled orders in the listing, after every status that is still moving")
    void keepsCanceledOrdersLast() {
        UUID canceled = givenOrder(ServiceOrderStatus.CANCELED, REFERENCE);
        UUID received = givenOrder(ServiceOrderStatus.RECEIVED, REFERENCE.plusDays(1));
        repository.flush();

        assertThat(listedIdsAmong(canceled, received)).containsExactly(received, canceled);
    }

    /**
     * O contexto é compartilhado com os outros testes de integração, então a listagem pode
     * trazer OS que não são deste teste: só a ordem relativa entre as nossas é observada.
     */
    private List<UUID> listedIdsAmong(UUID... expected) {
        Set<UUID> mine = Set.of(expected);
        return repository.getAllServiceOrdersFiltered().stream()
                .map(JPAServiceOrderEntity::getId)
                .filter(mine::contains)
                .toList();
    }

    private UUID givenOrder(ServiceOrderStatus status, LocalDateTime openedAt) {
        UUID id = UUID.randomUUID();
        repository.save(new JPAServiceOrderEntity(id, UUID.randomUUID(), UUID.randomUUID(),
                "problema", null, status, openedAt, null, null, ServiceOrderPriority.LOW));
        return id;
    }
}
