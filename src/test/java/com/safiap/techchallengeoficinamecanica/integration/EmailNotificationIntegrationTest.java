package com.safiap.techchallengeoficinamecanica.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Liga o envio de e-mail (notifications.email.enabled=true) e verifica que uma mudança de status
 * da ordem de serviço chega ao JavaMailSender. O executor assíncrono é trocado por um síncrono
 * para o teste ser determinístico.
 */
@SpringBootTest(properties = {
        "notifications.email.enabled=true",
        "notifications.email.from=nao-responda@oficina.local",
        "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EmailNotificationIntegrationTest.SyncNotificationExecutorConfig.class)
class EmailNotificationIntegrationTest {

    @TestConfiguration
    static class SyncNotificationExecutorConfig {
        @Bean("notificationExecutor")
        TaskExecutor notificationExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JavaMailSender javaMailSender;

    /** O adapter monta um MimeMessage; sem isto o mock devolveria null. */
    @BeforeEach
    void stubMimeMessage() {
        when(javaMailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new java.util.Properties())));
    }

    @Test
    @DisplayName("sends an email to the customer when the service order changes status")
    void sendsEmailOnStatusChange() throws Exception {
        Map<String, Object> credentials = map(
                "email", "notify@oficina.com",
                "password", "senha123");

        mockMvc.perform(post("/auth/register").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("bootstrap").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(credentials)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(credentials)))
                .andExpect(status().isOk())
                .andReturn();
        String token = field(loginResult, "accessToken");

        MvcResult customerResult = mockMvc.perform(authPost("/customers/register", map(
                        "name", "Maria Souza",
                        "email", "maria@email.com",
                        "phone", "11988887777",
                        "cnpjCpf", "52998224725"), token))
                .andExpect(status().isCreated())
                .andReturn();
        String customerId = field(customerResult, "customerId");

        Map<String, Object> vehicle = map(
                "carLicensePlate", "XYZ9K88",
                "model", "Corolla",
                "manufacturer", "Toyota",
                "kilometers", 30000,
                "year", 2021);
        vehicle.put("customerId", customerId);
        MvcResult vehicleResult = mockMvc.perform(authPost("/vehicles/register", vehicle, token))
                .andExpect(status().isCreated())
                .andReturn();
        String vehicleId = field(vehicleResult, "vehicleId");

        Map<String, Object> openSo = map("problemDescription", "Ruído na suspensão");
        openSo.put("customerId", customerId);
        openSo.put("vehicleId", vehicleId);
        MvcResult soResult = mockMvc.perform(authPost("/service-orders", openSo, token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andReturn();
        String serviceOrderId = field(soResult, "serviceOrderId");

        MimeMessage opened = lastMailSent(1);
        assertThat(opened.getFrom()[0].toString()).isEqualTo("nao-responda@oficina.local");
        assertThat(opened.getAllRecipients()[0].toString()).isEqualTo("maria@email.com");
        assertThat(subjectOf(opened))
                .contains(serviceOrderId.substring(0, 8))
                .contains("recebida");
        assertThat(bodyOf(opened))
                .contains("Olá, Maria Souza!")
                .contains("Sua ordem de serviço foi recebida.")
                .contains("Toyota Corolla (XYZ9K88)");

        mockMvc.perform(authPatch("/service-orders/" + serviceOrderId + "/start-diagnosis", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_DIAGNOSIS"));

        assertThat(subjectOf(lastMailSent(2))).contains("em diagnóstico");
    }

    /** Verifica o total de envios acumulados e devolve a última mensagem enviada. */
    private MimeMessage lastMailSent(int expectedTotal) {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, times(expectedTotal)).send(captor.capture());
        return captor.getValue();
    }

    private String subjectOf(MimeMessage mail) {
        try {
            return mail.getSubject();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String bodyOf(MimeMessage mail) {
        try {
            return mail.getContent().toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private String json(Map<String, Object> map) throws Exception {
        return objectMapper.writeValueAsString(map);
    }

    private String field(MvcResult result, String field) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get(field).asText();
    }

    private MockHttpServletRequestBuilder authPost(String url, Map<String, Object> body, String token) throws Exception {
        return post(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
    }

    private MockHttpServletRequestBuilder authPatch(String url, String token) {
        return patch(url).header("Authorization", "Bearer " + token);
    }
}
