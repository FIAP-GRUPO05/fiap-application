package com.safiap.techchallengeoficinamecanica.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServiceOrderFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode fixtures;

    @Test
    @DisplayName("walks through the full service order flow end to end")
    void fullServiceOrderLifecycle() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body("register"))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body("login"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = field(loginResult, "accessToken");
        assertThat(token).isNotBlank();

        MvcResult customerResult = mockMvc.perform(authPost("/customers/register", body("customer"), token))
                .andExpect(status().isCreated())
                .andReturn();
        String customerId = field(customerResult, "customerId");

        Map<String, Object> vehicle = body("vehicle");
        vehicle.put("customerId", customerId);
        MvcResult vehicleResult = mockMvc.perform(authPost("/vehicles/register", vehicle, token))
                .andExpect(status().isCreated())
                .andReturn();
        String vehicleId = field(vehicleResult, "vehicleId");

        MvcResult partResult = mockMvc.perform(authPost("/part", body("part"), token))
                .andExpect(status().isCreated())
                .andReturn();
        String partId = field(partResult, "id");

        MvcResult serviceResult = mockMvc.perform(authPost("/service", body("service"), token))
                .andExpect(status().isCreated())
                .andReturn();
        String serviceId = field(serviceResult, "id");

        Map<String, Object> openSo = body("openServiceOrder");
        openSo.put("customerId", customerId);
        openSo.put("vehicleId", vehicleId);
        MvcResult soResult = mockMvc.perform(authPost("/service-orders", openSo, token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andReturn();
        String serviceOrderId = field(soResult, "serviceOrderId");

        mockMvc.perform(authPatch("/service-orders/" + serviceOrderId + "/start-diagnosis", null, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_DIAGNOSIS"));

        Map<String, Object> addPart = body("addPart");
        addPart.put("itemId", partId);
        mockMvc.perform(authPost("/service-orders/" + serviceOrderId + "/budget/parts", addPart, token))
                .andExpect(status().isCreated());

        Map<String, Object> budgetItems = body("budgetItems");
        itemsOf(budgetItems).get(0).put("itemId", serviceId);
        mockMvc.perform(authPost("/service-orders/" + serviceOrderId + "/budget/items", budgetItems, token))
                .andExpect(status().isCreated());

        MvcResult budgetResult = mockMvc.perform(authGet("/service-orders/" + serviceOrderId + "/budget", token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode budget = objectMapper.readTree(budgetResult.getResponse().getContentAsString());
        assertThat(budget.get("totalAmount").decimalValue()).isEqualByComparingTo(new BigDecimal("239.90"));

        String serviceBudgetItemId = null;
        for (JsonNode item : budget.get("items")) {
            if ("SERVICE".equals(item.get("type").asText())) {
                serviceBudgetItemId = item.get("budgetItemId").asText();
            }
        }
        assertThat(serviceBudgetItemId).isNotNull();

        String expectedDiagnosis = fixtures().get("finalizeDiagnosis").get("diagnosis").asText();
        mockMvc.perform(authPatch("/service-orders/" + serviceOrderId + "/finalize-diagnosis", body("finalizeDiagnosis"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.diagnosis").value(expectedDiagnosis));

        // O cliente precisa aprovar o orcamento antes: sem isso o /execute responde 409.
        // A aprovacao e um POST e devolve a pagina de confirmacao, nao JSON.
        mockMvc.perform(post("/service-orders/" + serviceOrderId + "/budget/approve"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Orçamento aprovado")));

        mockMvc.perform(authPatch("/service-orders/" + serviceOrderId + "/execute", null, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EXECUTION"));

        mockMvc.perform(authPatch("/service-orders/" + serviceOrderId + "/budget/items/" + serviceBudgetItemId + "/complete", null, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.type == 'SERVICE')].completedAt").exists());

        mockMvc.perform(authPatch("/service-orders/" + serviceOrderId + "/finalize", null, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"));

        mockMvc.perform(authPatch("/service-orders/" + serviceOrderId + "/deliver", null, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        // Exclusao logica: entregue sai da fila de trabalho, mas continua consultavel por id
        // e pelo filtro explicito de status.
        mockMvc.perform(authGet("/service-orders", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.serviceOrderId == '" + serviceOrderId + "')]").doesNotExist());
        mockMvc.perform(authGet("/service-orders?status=DELIVERED", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.serviceOrderId == '" + serviceOrderId + "')]").exists());
        mockMvc.perform(authGet("/service-orders/" + serviceOrderId, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        MvcResult metricsResult = mockMvc.perform(authGet("/service-orders/" + serviceOrderId + "/metrics/average-execution-time", token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode metrics = objectMapper.readTree(metricsResult.getResponse().getContentAsString());
        boolean serviceMetricFound = false;
        for (JsonNode metric : metrics) {
            if (serviceId.equals(metric.get("serviceId").asText())) {
                serviceMetricFound = true;
                assertThat(metric.get("sampleCount").asLong()).isGreaterThanOrEqualTo(1);
                assertThat(metric.get("averageMinutes").asDouble()).isGreaterThanOrEqualTo(0.0);
            }
        }
        assertThat(serviceMetricFound).isTrue();

        walkShortPath(token, customerId, vehicleId, partId, serviceId);
    }

    /**
     * Segundo ciclo completo de OS. O orçamento é montado em uma chamada só (POST /budget/items)
     * e finalizado junto do diagnóstico — os itens enviados no corpo do finalize-diagnosis são
     * ignorados desde o fix de itens duplicados.
     */
    private void walkShortPath(String token, String customerId, String vehicleId,
                               String partId, String serviceId) throws Exception {
        Map<String, Object> openSo = body("openServiceOrder");
        openSo.put("customerId", customerId);
        openSo.put("vehicleId", vehicleId);
        MvcResult soResult = mockMvc.perform(authPost("/service-orders", openSo, token))
                .andExpect(status().isCreated())
                .andReturn();
        String serviceOrderId = field(soResult, "serviceOrderId");

        mockMvc.perform(authPatch("/service-orders/" + serviceOrderId + "/start-diagnosis", null, token))
                .andExpect(status().isOk());

        Map<String, Object> finalizeDiagnosis = body("finalizeDiagnosisWithItems");
        itemsOf(finalizeDiagnosis).get(0).put("itemId", partId);
        itemsOf(finalizeDiagnosis).get(1).put("itemId", serviceId);

        mockMvc.perform(authPost("/service-orders/" + serviceOrderId + "/budget/items",
                Map.of("items", itemsOf(finalizeDiagnosis)), token))
                .andExpect(status().isCreated());

        mockMvc.perform(authPatch("/service-orders/" + serviceOrderId + "/finalize-diagnosis", finalizeDiagnosis, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"));

        MvcResult budgetResult = mockMvc.perform(authGet("/service-orders/" + serviceOrderId + "/budget", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andReturn();
        JsonNode budget = objectMapper.readTree(budgetResult.getResponse().getContentAsString());
        assertThat(budget.get("totalAmount").decimalValue()).isEqualByComparingTo(new BigDecimal("239.90"));

        String serviceBudgetItemId = null;
        for (JsonNode item : budget.get("items")) {
            if ("SERVICE".equals(item.get("type").asText())) {
                serviceBudgetItemId = item.get("budgetItemId").asText();
            }
        }
        assertThat(serviceBudgetItemId).isNotNull();

        // O cliente precisa aprovar o orcamento antes: sem isso o /execute responde 409.
        // A aprovacao e um POST e devolve a pagina de confirmacao, nao JSON.
        mockMvc.perform(post("/service-orders/" + serviceOrderId + "/budget/approve"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Orçamento aprovado")));

        mockMvc.perform(authPatch("/service-orders/" + serviceOrderId + "/execute", null, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EXECUTION"));

        mockMvc.perform(authPatch("/service-orders/" + serviceOrderId + "/budget/items/" + serviceBudgetItemId + "/complete", null, token))
                .andExpect(status().isOk());

        mockMvc.perform(authPatch("/service-orders/" + serviceOrderId + "/finalize", null, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"));

        mockMvc.perform(authPatch("/service-orders/" + serviceOrderId + "/deliver", null, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    @DisplayName("answers 400 with field errors instead of 500 when the payload is invalid")
    void invalidPayloadsAreRejectedWithBadRequest() throws Exception {
        Map<String, Object> account = Map.of("email", "validation@oficina.com", "password", "senha123");
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(account)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(account)))
                .andExpect(status().isOk())
                .andReturn();
        String token = field(loginResult, "accessToken");

        // ids ausentes: antes estourava 500 (The given id must not be null) no repositório
        Map<String, Object> orderWithoutIds = new java.util.HashMap<>();
        orderWithoutIds.put("problemDescription", "Carro fazendo barulho ao frear");
        mockMvc.perform(authPost("/service-orders", orderWithoutIds, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'customerId')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'vehicleId')]").exists());

        // uuid malformado no path
        mockMvc.perform(authGet("/service-orders/nao-e-um-uuid", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'serviceOrderId' must be a valid UUID"));

        UUID anyOrder = UUID.randomUUID();
        mockMvc.perform(authPost("/service-orders/" + anyOrder + "/budget/items", Map.of("items", List.of()), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'items')]").exists());

        Map<String, Object> itemWithoutType = new java.util.HashMap<>();
        itemWithoutType.put("itemId", UUID.randomUUID().toString());
        itemWithoutType.put("description", "Peça sem tipo");
        itemWithoutType.put("quantity", 0);
        mockMvc.perform(authPost("/service-orders/" + anyOrder + "/budget/items",
                        Map.of("items", List.of(itemWithoutType)), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'items[0].type')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'items[0].quantity')]").exists());

        mockMvc.perform(authPatch("/service-orders/" + anyOrder + "/finalize-diagnosis", Map.of(), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'diagnosis')]").exists());
    }

    @Test
    @DisplayName("rejects access to a protected endpoint without an authentication token")
    void protectedEndpointWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/service-orders").param("status", "RECEIVED"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode fixtures() throws Exception {
        if (fixtures == null) {
            try (InputStream is = new ClassPathResource("requests.json").getInputStream()) {
                fixtures = objectMapper.readTree(is);
            }
        }
        return fixtures;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> itemsOf(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("items");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(String key) throws Exception {
        return objectMapper.convertValue(fixtures().get(key), Map.class);
    }

    private String json(Map<String, Object> map) throws Exception {
        return objectMapper.writeValueAsString(map);
    }

    private String field(MvcResult result, String field) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get(field).asText();
    }

    private MockHttpServletRequestBuilder authPost(String url, Map<String, Object> body, String token) throws Exception {
        MockHttpServletRequestBuilder rb = post(url).header("Authorization", "Bearer " + token);
        if (body != null) {
            rb = rb.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return rb;
    }

    private MockHttpServletRequestBuilder authPatch(String url, Map<String, Object> body, String token) throws Exception {
        MockHttpServletRequestBuilder rb = patch(url).header("Authorization", "Bearer " + token);
        if (body != null) {
            rb = rb.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return rb;
    }

    private MockHttpServletRequestBuilder authGet(String url, String token) {
        return get(url).header("Authorization", "Bearer " + token);
    }
}
