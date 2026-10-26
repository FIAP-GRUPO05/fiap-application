package com.safiap.techchallengeoficinamecanica.modules.notifications.infrastructure.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safiap.techchallengeoficinamecanica.modules.notifications.domain.value_objects.EmailMessage;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LambdaEmailSenderAdapterTest {
    private final LambdaClient client = mock(LambdaClient.class);
    private final LambdaEmailSenderAdapter adapter = new LambdaEmailSenderAdapter(new ObjectMapper(), client);
    private final EmailMessage message = new EmailMessage("client@example.test", "OS", "Concluída", false);

    @Test
    void invokesEmailLambdaWithSerializedPayload() {
        when(client.invoke(any(InvokeRequest.class))).thenReturn(InvokeResponse.builder()
                .statusCode(200).payload(SdkBytes.fromUtf8String("{\"statusCode\":200,\"messageId\":\"ses-id\"}")).build());
        adapter.send(message);
        var request = org.mockito.ArgumentCaptor.forClass(InvokeRequest.class);
        verify(client).invoke(request.capture());
        assertThat(request.getValue().functionName()).isEqualTo("fiap-email-develop:live");
        assertThat(request.getValue().payload().asUtf8String()).contains("client@example.test");
    }

    @Test
    void propagatesCorrelationAndRejectsProviderErrorsWithoutExposingPayload() {
        var custom = new LambdaEmailSenderAdapter(new ObjectMapper(), client, "fiap-email-prod:live");
        when(client.invoke(any(InvokeRequest.class))).thenReturn(InvokeResponse.builder()
                .statusCode(200).payload(SdkBytes.fromUtf8String("{\"statusCode\":500,\"body\":\"private recipient\"}")).build());
        org.slf4j.MDC.put("correlationId", "notification-123");
        try {
            assertThatThrownBy(() -> custom.send(message)).isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContaining("private recipient");
        } finally { org.slf4j.MDC.clear(); }
        var request = org.mockito.ArgumentCaptor.forClass(InvokeRequest.class);
        verify(client).invoke(request.capture());
        assertThat(request.getValue().functionName()).isEqualTo("fiap-email-prod:live");
        assertThat(request.getValue().payload().asUtf8String()).contains("notification-123");
    }

    @Test
    void rejectsFunctionErrorAndHttpFailure() {
        when(client.invoke(any(InvokeRequest.class))).thenReturn(InvokeResponse.builder()
                .statusCode(200).functionError("Unhandled").payload(SdkBytes.fromUtf8String("failed")).build());
        assertThatThrownBy(() -> adapter.send(message)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unhandled");
        when(client.invoke(any(InvokeRequest.class))).thenReturn(InvokeResponse.builder()
                .statusCode(500).payload(SdkBytes.fromUtf8String("failed")).build());
        assertThatThrownBy(() -> adapter.send(message)).isInstanceOf(IllegalStateException.class).hasMessageContaining("500");
    }
}
