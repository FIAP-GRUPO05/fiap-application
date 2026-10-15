package com.safiap.techchallengeoficinamecanica.modules.shared.infrastructure.observability;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.TraceMetadata;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Correlação entre requisições: reaproveita o X-Correlation-Id recebido (ou gera um), devolve no
 * response e coloca no MDC junto do trace.id/span.id do New Relic. Assim todo log da requisição
 * sai com os mesmos ids, e o New Relic liga o log ao trace distribuído pelo trace.id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";

    // Aceita só ids "bem comportados": o valor vai para logs e headers de resposta
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        MDC.put(ObservabilityKeys.CORRELATION_ID, correlationId);
        putTraceMetadata();
        NewRelic.addCustomParameter(ObservabilityKeys.CORRELATION_ID, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(ObservabilityKeys.CORRELATION_ID);
            MDC.remove(ObservabilityKeys.TRACE_ID);
            MDC.remove(ObservabilityKeys.SPAN_ID);
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String received = request.getHeader(HEADER);
        if (received != null && VALID_ID.matcher(received).matches()) {
            return received;
        }
        return UUID.randomUUID().toString();
    }

    private void putTraceMetadata() {
        // Sem o agente (local, testes) a API devolve ids vazios
        TraceMetadata traceMetadata = NewRelic.getAgent().getTraceMetadata();
        if (!traceMetadata.getTraceId().isEmpty()) {
            MDC.put(ObservabilityKeys.TRACE_ID, traceMetadata.getTraceId());
            MDC.put(ObservabilityKeys.SPAN_ID, traceMetadata.getSpanId());
        }
    }
}
