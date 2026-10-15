package com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.responses.AverageServiceTimeResponse;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.repositories.BudgetRepository;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.repositories.ServiceDuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetAverageServiceTimeUseCaseTest {

    private final BudgetRepository budgetRepository = mock(BudgetRepository.class);
    private final GetAverageServiceTimeUseCase useCase = new GetAverageServiceTimeUseCase(budgetRepository);

    @Test
    @DisplayName("groups by service and computes the average in minutes with the sample count")
    void averagesPerService() {
        UUID serviceOrderId = UUID.randomUUID();
        UUID serviceA = UUID.randomUUID();
        UUID serviceB = UUID.randomUUID();
        when(budgetRepository.findServiceDurations(serviceOrderId)).thenReturn(List.of(
                new ServiceDuration(serviceA, 60),
                new ServiceDuration(serviceA, 120),
                new ServiceDuration(serviceB, 300)
        ));

        Map<UUID, AverageServiceTimeResponse> byService = useCase.execute(serviceOrderId).stream()
                .collect(Collectors.toMap(AverageServiceTimeResponse::serviceId, r -> r));

        assertThat(byService.get(serviceA).averageMinutes()).isEqualTo(1.5);
        assertThat(byService.get(serviceA).sampleCount()).isEqualTo(2);
        assertThat(byService.get(serviceB).averageMinutes()).isEqualTo(5.0);
        assertThat(byService.get(serviceB).sampleCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("returns an empty list when there are no completed services")
    void emptyWhenNoSamples() {
        when(budgetRepository.findServiceDurations(any())).thenReturn(List.of());
        assertThat(useCase.execute(UUID.randomUUID())).isEmpty();
    }
}
