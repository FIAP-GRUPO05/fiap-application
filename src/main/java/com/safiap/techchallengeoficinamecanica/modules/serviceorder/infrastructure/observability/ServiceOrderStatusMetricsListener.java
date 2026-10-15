package com.safiap.techchallengeoficinamecanica.modules.serviceorder.infrastructure.observability;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.events.ServiceOrderStatusChangedEvent;
import com.safiap.techchallengeoficinamecanica.modules.shared.infrastructure.observability.CustomEventRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

import static net.logstash.logback.argument.StructuredArguments.kv;


@Component
public class ServiceOrderStatusMetricsListener {

    static final String EVENT_TYPE = "ServiceOrderStatusChanged";

    private static final Logger log = LoggerFactory.getLogger(ServiceOrderStatusMetricsListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ServiceOrderStatusChangedEvent event) {
        try {
            Long secondsInPreviousStatus = event.timeInPreviousStatus() != null
                    ? event.timeInPreviousStatus().toSeconds()
                    : null;

            Map<String, Object> attributes = new HashMap<>();
            
            attributes.put("serviceOrderId", event.serviceOrderId().toString());
            attributes.put("previousStatus", event.previousStatus() != null ? event.previousStatus().name() : null);
            attributes.put("newStatus", event.newStatus().name());
            attributes.put("timeInPreviousStatusSeconds", secondsInPreviousStatus);
            CustomEventRecorder.record(EVENT_TYPE, attributes);

            log.info("Service order status changed",
                    kv("serviceOrderId", event.serviceOrderId()),
                    kv("previousStatus", event.previousStatus()),
                    kv("newStatus", event.newStatus()),
                    kv("timeInPreviousStatusSeconds", secondsInPreviousStatus));
        } catch (RuntimeException e) {
            
            log.warn("Failed to record status change metric of service order {}", event.serviceOrderId(), e);

        }
    }
}
