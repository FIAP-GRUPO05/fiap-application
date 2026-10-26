package com.safiap.techchallengeoficinamecanica.modules.notifications.infrastructure.adapters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safiap.techchallengeoficinamecanica.modules.notifications.application.ports.EmailSenderPort;
import com.safiap.techchallengeoficinamecanica.modules.notifications.domain.value_objects.EmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvocationType;

public class LambdaEmailSenderAdapter implements EmailSenderPort {
    private static final Logger log = LoggerFactory.getLogger(LambdaEmailSenderAdapter.class);
    private final LambdaClient client;
    private final ObjectMapper mapper;
    private final String function;

    public LambdaEmailSenderAdapter(ObjectMapper mapper, LambdaClient client, String function) {
        if (function == null || function.isBlank()) throw new IllegalArgumentException("Notification Lambda is required");
        this.mapper = mapper;
        this.client = client;
        this.function = function;
    }

    LambdaEmailSenderAdapter(ObjectMapper mapper, LambdaClient client) {
        this(mapper, client, "fiap-email-develop:live");
    }

    @Override
    public void send(EmailMessage message) {
        try {
            var payload = mapper.valueToTree(message);
            String correlation = MDC.get("correlationId");
            if (correlation != null) ((com.fasterxml.jackson.databind.node.ObjectNode) payload).put("correlationId", correlation);
            var response = client.invoke(InvokeRequest.builder().functionName(function)
                    .invocationType(InvocationType.REQUEST_RESPONSE)
                    .payload(SdkBytes.fromUtf8String(mapper.writeValueAsString(payload))).build());
            if (response.statusCode() == null || response.statusCode() != 200 || response.functionError() != null) {
                throw new IllegalStateException("Notification Lambda failed (status " + response.statusCode()
                        + ", functionError " + response.functionError() + ")");
            }
            var result = mapper.readTree(response.payload().asUtf8String());
            if (result.path("statusCode").asInt() != 200 || result.path("messageId").asText().isBlank()) {
                throw new IllegalStateException("Notification provider did not acknowledge delivery");
            }
            log.info("Notification provider accepted message; function={}, messageId={}", function, result.path("messageId").asText());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Invalid notification Lambda contract", error);
        }
    }
}
