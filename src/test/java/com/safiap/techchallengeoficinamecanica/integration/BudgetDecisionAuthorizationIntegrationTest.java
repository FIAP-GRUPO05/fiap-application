package com.safiap.techchallengeoficinamecanica.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real signed JWTs exercise the filter, roles, object ownership and business state together. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BudgetDecisionAuthorizationIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtEncoder encoder;
    private final Map<String, String> owners = new HashMap<>();

    @Test
    void publicEmailLinkOnlyReturnsIdentificationAndNeverRevealsBudgetDetails() throws Exception {
        String id = givenOrderAwaitingApproval("landing");
        String known = mockMvc.perform(get(url(id, "/decision?decision=approve")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("Identifique-se")))
                .andExpect(content().string(not(containsString("Alinhamento"))))
                .andExpect(content().string(not(containsString("R$ 200,00"))))
                .andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(get(url(UUID.randomUUID().toString(), "/decision")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(known.replaceAll("nonce=\"[^\"]+\"", "nonce=\"\""))
                .isEqualTo(unknown.replaceAll("nonce=\"[^\"]+\"", "nonce=\"\""));
        assertBudget(id, "FINALIZED");
    }

    @Test
    void anonymousRequestsCannotReadOrDecideEvenWithTheCorrectOrderIdentifier() throws Exception {
        String id = givenOrderAwaitingApproval("anonymous");
        for (String suffix : List.of("", "/decision/view")) {
            mockMvc.perform(get(url(id, suffix))).andExpect(status().isUnauthorized());
        }
        for (String suffix : List.of("/approve", "/reject")) {
            mockMvc.perform(post(url(id, suffix))).andExpect(status().isUnauthorized());
        }
        assertBudget(id, "FINALIZED");
    }

    @Test
    void anotherCustomersTokenCannotReadApproveOrRejectAndTheStateIsPreserved() throws Exception {
        String id = givenOrderAwaitingApproval("foreign");
        String foreign = token("CUSTOMER", UUID.randomUUID().toString(), 300);
        for (String suffix : List.of("", "/decision/view")) {
            mockMvc.perform(get(url(id, suffix)).header("Authorization", "Bearer " + foreign))
                    .andExpect(status().isForbidden());
        }
        for (String suffix : List.of("/approve", "/reject")) {
            mockMvc.perform(post(url(id, suffix)).header("Authorization", "Bearer " + foreign))
                    .andExpect(status().isForbidden());
        }
        assertBudget(id, "FINALIZED");
    }

    @Test
    void ownerCanConsultAndApproveWithIdempotencyAndOppositeDecisionStillConflicts() throws Exception {
        String id = givenOrderAwaitingApproval("owner-approve");
        String auth = "Bearer " + owner(id);
        mockMvc.perform(get(url(id, "")).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(200));
        mockMvc.perform(get(url(id, "/decision/view?decision=reject")).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("Alinhamento")));
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post(url(id, "/approve")).header("Authorization", auth))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("Orçamento aprovado")));
        }
        mockMvc.perform(post(url(id, "/reject")).header("Authorization", auth))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("já foi respondido")));
        mockMvc.perform(get(url(id, "/decision/view")).header("Authorization", auth))
                .andExpect(content().string(containsString("Orçamento aprovado")))
                .andExpect(content().string(not(containsString("<form"))));
        assertBudget(id, "APPROVED");
    }

    @Test
    void ownerCanRejectWithIdempotencyAndTheOrderIsCancelled() throws Exception {
        String id = givenOrderAwaitingApproval("owner-reject");
        String auth = "Bearer " + owner(id);
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post(url(id, "/reject")).header("Authorization", auth))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("Cancelada")));
        }
        mockMvc.perform(post(url(id, "/approve")).header("Authorization", auth)).andExpect(status().isConflict());
        mockMvc.perform(get(url(id, "/decision/view")).header("Authorization", auth))
                .andExpect(content().string(containsString("Orçamento recusado")));
        assertBudget(id, "DECLINED");
    }

    @Test
    void authorizedStaffKeepTheirOperationalAccess() throws Exception {
        for (String role : List.of("USER", "ADMIN")) {
            String id = givenOrderAwaitingApproval("staff-" + role.toLowerCase());
            String auth = "Bearer " + token(role, null, 300);
            mockMvc.perform(get(url(id, "")).header("Authorization", auth)).andExpect(status().isOk());
            mockMvc.perform(get(url(id, "/decision/view")).header("Authorization", auth)).andExpect(status().isOk());
            mockMvc.perform(post(url(id, role.equals("USER") ? "/approve" : "/reject")).header("Authorization", auth))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void invalidExpiredOrUnboundCustomerTokensCannotDecide() throws Exception {
        String id = givenOrderAwaitingApproval("claims");
        for (String invalid : List.of("invalid.jwt", token("CUSTOMER", owners.get(id), -60))) {
            mockMvc.perform(get(url(id, "")).header("Authorization", "Bearer " + invalid)).andExpect(status().isUnauthorized());
            mockMvc.perform(post(url(id, "/approve")).header("Authorization", "Bearer " + invalid)).andExpect(status().isUnauthorized());
        }
        for (String invalid : List.of(token("CUSTOMER", null, 300), token("CUSTOMER", "not-a-uuid", 300), token("UNKNOWN", null, 300))) {
            mockMvc.perform(get(url(id, "")).header("Authorization", "Bearer " + invalid)).andExpect(status().isForbidden());
            mockMvc.perform(post(url(id, "/reject")).header("Authorization", "Bearer " + invalid)).andExpect(status().isForbidden());
        }
        mockMvc.perform(post(url(id, "/items")).header("Authorization", "Bearer " + owner(id))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("items", List.of(map(
                        "type", "SERVICE", "itemId", UUID.randomUUID(), "description", "blocked", "quantity", 1))))))
                .andExpect(status().isForbidden());
        assertBudget(id, "FINALIZED");
    }

    private String url(String id, String suffix) { return "/service-orders/" + id + "/budget" + suffix; }
    private String owner(String id) { return token("CUSTOMER", owners.get(id), 300); }
    private void assertBudget(String id, String expected) throws Exception {
        mockMvc.perform(get(url(id, "")).header("Authorization", "Bearer " + owner(id)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(expected));
    }
    private String token(String role, String customerId, long expires) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().subject("authenticated-client@example.test").issuedAt(now.minusSeconds(120))
                .expiresAt(now.plusSeconds(expires)).claim("roles", List.of(role));
        if (customerId != null) claims.claim("customerId", customerId);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    /** Abre uma OS e a leva ate AWAITING_APPROVAL usando um token de oficina. */
    private String givenOrderAwaitingApproval(String suffix) throws Exception {
        String email = "publico-" + suffix + "-" + UUID.randomUUID() + "@oficina.local";
        mockMvc.perform(post("/auth/register").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("bootstrap").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
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
        owners.put(serviceOrderId, customerId);

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
