package com.safiap.techchallengeoficinamecanica.modules.shared.infrastructure.observability;

import com.newrelic.api.agent.NewRelic;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

/**
 * Grava eventos customizados no New Relic (consultáveis via NRQL: FROM <tipo>). Sem o agente
 * a chamada não faz nada.
 */
public final class CustomEventRecorder {

    private CustomEventRecorder() {
    }

    public static void record(String eventType, Map<String, Object> attributes) {
        Map<String, Object> event = new HashMap<>();
        // O agente rejeita atributos nulos
        attributes.forEach((key, value) -> {
            if (value != null) {
                event.put(key, value);
            }
        });
        String correlationId = MDC.get(ObservabilityKeys.CORRELATION_ID);
        if (correlationId != null) {
            event.put(ObservabilityKeys.CORRELATION_ID, correlationId);
        }
        NewRelic.getAgent().getInsights().recordCustomEvent(eventType, event);
    }
}
