package com.safiap.techchallengeoficinamecanica.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"notifications.email=LAMBDA", "notifications.lambda.function-name=function",
    "notifications.lambda.endpoint=http://127.0.0.1:19000", "notifications.executor.concurrency=1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="FIAP_LOCAL_LAMBDA_TEST", matches="true")
class LambdaEmailDeliveryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void committedOrderAndDiagnosisDeliverCorrelatedEmailsThroughLambdaRuntime() throws Exception {
        String correlation = "domain-" + UUID.randomUUID();
        JsonNode customer = create("/customers/register", Map.of("name", "Cliente Local", "email", "journey@example.test",
                "phone", "11988887777", "cnpjCpf", "52998224725"), correlation);
        JsonNode vehicle = create("/vehicles/register", Map.of("customerId", customer.path("customerId").asText(),
                "carLicensePlate", "XYZ9K88", "model", "Corolla", "manufacturer", "Toyota", "year", 2021,
                "kilometers", 30000), correlation);
        JsonNode order = create("/service-orders", Map.of("customerId", customer.path("customerId").asText(),
                "vehicleId", vehicle.path("vehicleId").asText(), "problemDescription", "Ruido local"), correlation);
        String id = order.path("serviceOrderId").asText();
        mvc.perform(patch("/service-orders/" + id + "/start-diagnosis")
                .with(user("operator").roles("ADMIN")).header("X-Correlation-Id", correlation))
                .andExpect(status().isOk());
        int captured = 0;
        for (int attempt = 0; attempt < 100 && captured != 2; attempt++) {
            var http = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:18025/api/v2/messages")).GET().build(), HttpResponse.BodyHandlers.ofString());
            captured = 0;
            for (var message : mapper.readTree(http.body()).path("items")) {
                var headers = message.path("Content").path("Headers");
                if (headers.path("X-Correlation-Id").path(0).asText().equals(correlation)) {
                    assertThat(headers.path("To").path(0).asText()).contains("journey@example.test");
                    assertThat(jakarta.mail.internet.MimeUtility.decodeText(headers.path("Subject").path(0).asText())).contains(id.substring(0, 8));
                    captured++;
                }
            }
            if (captured != 2) Thread.sleep(100);
        }
        assertThat(captured).as("OS opened and diagnosis started must each deliver an email").isEqualTo(2);
    }

    private JsonNode create(String path, Map<String, Object> body, String correlation) throws Exception {
        var response = mvc.perform(post(path).with(user("operator").roles("ADMIN"))
                .header("X-Correlation-Id", correlation).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsString());
    }
}
