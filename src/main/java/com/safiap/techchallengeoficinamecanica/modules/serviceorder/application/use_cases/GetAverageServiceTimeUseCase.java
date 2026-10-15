package com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.responses.AverageServiceTimeResponse;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.repositories.BudgetRepository;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.repositories.ServiceDuration;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GetAverageServiceTimeUseCase {

    private final BudgetRepository budgetRepository;

    public GetAverageServiceTimeUseCase(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    public List<AverageServiceTimeResponse> execute(UUID serviceOrderId) {
        Map<UUID, List<Long>> durationsByService = budgetRepository.findServiceDurations(serviceOrderId).stream()
                .collect(Collectors.groupingBy(
                        ServiceDuration::serviceId,
                        Collectors.mapping(ServiceDuration::durationSeconds, Collectors.toList())));

        return durationsByService.entrySet().stream()
                .map(entry -> {
                    long sampleCount = entry.getValue().size();
                    double averageSeconds = entry.getValue().stream()
                            .mapToLong(Long::longValue)
                            .average()
                            .orElse(0);
                    return new AverageServiceTimeResponse(entry.getKey(), toRoundedMinutes(averageSeconds), sampleCount);
                })
                .sorted(Comparator.comparing(r -> r.serviceId().toString()))
                .toList();
    }

    private double toRoundedMinutes(double seconds) {
        return Math.round(seconds/60.0*100.0)/100.0;
    }
}
