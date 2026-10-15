package com.safiap.techchallengeoficinamecanica.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O cliente decide o orcamento pelo link do e-mail, sem token. Estes testes travam o contrato
 * desse fluxo: o GET do link e seguro, a decisao sai por POST idempotente, a decisao contraria
 * da 409 — e nada disso vaza permissao para o resto da API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BudgetDecisionPublicAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("the link from the email only shows the budget: it never decides by itself")
    void decisionPageIsSafe() throws Exception {
        String serviceOrderId = givenOrderAwaitingApproval("pagina");

        mockMvc.perform(get("/service-orders/" + serviceOrderId + "/budget/decision?decision=approve"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Alinhamento")))
                .andExpect(content().string(containsString("R$ 200,00")))
                .andExpect(content().string(containsString(
                        "action=\"/service-orders/" + serviceOrderId + "/budget/approve\"")))
                .andExpect(content().string(containsString(
                        "action=\"/service-orders/" + serviceOrderId + "/budget/reject\"")));

        // o GET nao pode ter decidido nada — e o que impede um prefetch do webmail de aprovar
        mockMvc.perform(get("/service-orders/" + serviceOrderId + "/budget"))
                .andExpect(jsonPath("$.status").value("FINALIZED"));
    }

    @Test
    @DisplayName("posting the approval registers it and answers with the confirmation page")
    void approvesWithoutTokenReturningPage() throws Exception {
        String serviceOrderId = givenOrderAwaitingApproval("aprova");

        mockMvc.perform(post("/service-orders/" + serviceOrderId + "/budget/approve"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Orçamento aprovado")))
                .andExpect(content().string(containsString(serviceOrderId.substring(0, 8))))
                .andExpect(content().string(containsString("Alinhamento")))
                .andExpect(content().string(containsString("R$ 200,00")));

        mockMvc.perform(get("/service-orders/" + serviceOrderId + "/budget"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("posting the rejection cancels the order and answers with its own page")
    void rejectsWithoutTokenReturningPage() throws Exception {
        String serviceOrderId = givenOrderAwaitingApproval("recusa");

        mockMvc.perform(post("/service-orders/" + serviceOrderId + "/budget/reject"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Orçamento recusado")))
                .andExpect(content().string(containsString("Cancelada")))
                .andExpect(content().string(containsString(serviceOrderId.substring(0, 8))));

        mockMvc.perform(get("/service-orders/" + serviceOrderId + "/budget"))
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    @DisplayName("repeating the same decision is idempotent: same 200, same state")
    void repeatingTheSameDecisionIsIdempotent() throws Exception {
        String approved = givenOrderAwaitingApproval("duplo-aprova");
        for (int click = 0; click < 3; click++) {
            mockMvc.perform(post("/service-orders/" + approved + "/budget/approve"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Orçamento aprovado")));
        }
        mockMvc.perform(get("/service-orders/" + approved + "/budget"))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        String rejected = givenOrderAwaitingApproval("duplo-recusa");
        for (int click = 0; click < 3; click++) {
            mockMvc.perform(post("/service-orders/" + rejected + "/budget/reject"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Orçamento recusado")));
        }
        mockMvc.perform(get("/service-orders/" + rejected + "/budget"))
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    @DisplayName("the opposite decision answers 409 with a readable page, never overwriting the first")
    void oppositeDecisionAnswersConflictPage() throws Exception {
        String serviceOrderId = givenOrderAwaitingApproval("oposta");

        mockMvc.perform(post("/service-orders/" + serviceOrderId + "/budget/approve"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/service-orders/" + serviceOrderId + "/budget/reject"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("já foi respondido")));

        mockMvc.perform(get("/service-orders/" + serviceOrderId + "/budget"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("after the decision the page shows the outcome instead of the buttons")
    void decisionPageShowsTheOutcomeOnceDecided() throws Exception {
        String serviceOrderId = givenOrderAwaitingApproval("desfecho");

        mockMvc.perform(post("/service-orders/" + serviceOrderId + "/budget/approve"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/service-orders/" + serviceOrderId + "/budget/decision"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Orçamento aprovado")))
                .andExpect(content().string(not(containsString("<form"))));
    }

    @Test
    @DisplayName("the public release does not leak to the rest of the API")
    void otherEndpointsStayProtected() throws Exception {
        String serviceOrderId = givenOrderAwaitingApproval("protegido");

        mockMvc.perform(get("/service-orders/" + serviceOrderId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/service-orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/service-orders/status/" + serviceOrderId)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/service-orders/" + serviceOrderId + "/execute")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/service-orders/" + serviceOrderId + "/budget/finalize")).andExpect(status().isUnauthorized());
        // nos caminhos de decisao so o POST foi liberado
        mockMvc.perform(get("/service-orders/" + serviceOrderId + "/budget/approve")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/service-orders/" + serviceOrderId + "/budget/reject")).andExpect(status().isUnauthorized());
        // a recusa pela API continua exigindo token; o link publico e o /budget/reject
        mockMvc.perform(patch("/service-orders/" + serviceOrderId + "/reject-budget")).andExpect(status().isUnauthorized());
    }

    /** Abre uma OS e a leva ate AWAITING_APPROVAL usando um token de oficina. */
    private String givenOrderAwaitingApproval(String suffix) throws Exception {
        String email = "publico-" + suffix + "-" + UUID.randomUUID() + "@oficina.local";
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "teste123"))))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "teste123"))))
                .andExpect(status().isOk()).andReturn();
        String token = read(login, "accessToken");

        MvcResult customer = mockMvc.perform(authPost("/customers/register", map(
                "name", "Cliente Publico",
                "email", "cli-" + suffix + "-" + UUID.randomUUID() + "@email.com",
                "phone", "11999998888",
                "cnpjCpf", generateCpf()), token))
                .andExpect(status().isCreated()).andReturn();
        String customerId = read(customer, "id", "customerId");

        MvcResult vehicle = mockMvc.perform(authPost("/vehicles/register", map(
                "customerId", customerId,
                "carLicensePlate", randomPlate(),
                "model", "Civic",
                "manufacturer", "Honda",
                "kilometers", 30000,
                "year", 2021), token))
                .andExpect(status().isCreated()).andReturn();
        String vehicleId = read(vehicle, "vehicleId", "id");

        MvcResult service = mockMvc.perform(authPost("/service", map(
                "name", "Alinhamento " + UUID.randomUUID(),
                "description", "Alinhamento e balanceamento",
                "price", new BigDecimal("200.00")), token))
                .andExpect(status().isCreated()).andReturn();
        String serviceId = read(service, "id", "serviceId");

        MvcResult order = mockMvc.perform(authPost("/service-orders", map(
                "customerId", customerId,
                "vehicleId", vehicleId,
                "problemDescription", "Volante tremendo"), token))
                .andExpect(status().isCreated()).andReturn();
        String serviceOrderId = read(order, "serviceOrderId");

        mockMvc.perform(patch("/service-orders/" + serviceOrderId + "/start-diagnosis")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        mockMvc.perform(authPost("/service-orders/" + serviceOrderId + "/budget/items", Map.of("items", List.of(map(
                "type", "SERVICE", "itemId", serviceId, "description", "Alinhamento", "quantity", 1))), token))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/service-orders/" + serviceOrderId + "/finalize-diagnosis")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("diagnosis", "Rodas desalinhadas"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"));

        return serviceOrderId;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authPost(
            String url, Map<String, Object> body, String token) throws Exception {
        return post(url).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json(body));
    }

    private Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String read(MvcResult result, String... candidateFields) throws Exception {
        var node = objectMapper.readTree(result.getResponse().getContentAsString());
        for (String field : candidateFields) {
            if (node.hasNonNull(field)) {
                return node.get(field).asText();
            }
        }
        throw new IllegalStateException("nenhum dos campos " + String.join("/", candidateFields) + " em " + node);
    }

    private String randomPlate() {
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        java.util.Random random = new java.util.Random();
        return "" + letters.charAt(random.nextInt(26)) + letters.charAt(random.nextInt(26))
                + letters.charAt(random.nextInt(26)) + random.nextInt(10)
                + letters.charAt(random.nextInt(26)) + random.nextInt(10) + random.nextInt(10);
    }

    /** CPF sintetico com digitos verificadores validos, para nao esbarrar na validacao. */
    private String generateCpf() {
        java.util.Random random = new java.util.Random();
        int[] digits = new int[11];
        for (int i = 0; i < 9; i++) {
            digits[i] = random.nextInt(10);
        }
        for (int position = 9; position < 11; position++) {
            int sum = 0;
            for (int i = 0; i < position; i++) {
                sum += digits[i] * (position + 1 - i);
            }
            int remainder = (sum * 10) % 11;
            digits[position] = remainder == 10 ? 0 : remainder;
        }
        StringBuilder cpf = new StringBuilder();
        for (int digit : digits) {
            cpf.append(digit);
        }
        return cpf.toString();
    }
}
