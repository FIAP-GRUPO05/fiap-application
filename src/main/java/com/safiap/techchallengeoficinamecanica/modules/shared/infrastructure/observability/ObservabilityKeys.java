package com.safiap.techchallengeoficinamecanica.modules.shared.infrastructure.observability;

/**
 * Nomes das chaves de correlação no MDC. trace.id e span.id seguem o padrão do New Relic,
 * que liga o log ao trace por esses atributos.
 */
public final class ObservabilityKeys {

    public static final String CORRELATION_ID = "correlationId";
    public static final String TRACE_ID = "trace.id";
    public static final String SPAN_ID = "span.id";

    private ObservabilityKeys() {
    }
}
