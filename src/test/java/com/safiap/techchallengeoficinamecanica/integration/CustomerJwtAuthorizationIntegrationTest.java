package com.safiap.techchallengeoficinamecanica.integration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.ServiceOrderPriority;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.ServiceOrderStatus;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.infrastructure.persistence.entities.JPAServiceOrderEntity;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.infrastructure.persistence.repositories.JPAServiceOrderRepository;
import com.safiap.techchallengeoficinamecanica.modules.register.infrastructure.persistence.entities.JPACustomerEntity;
import com.safiap.techchallengeoficinamecanica.modules.register.infrastructure.persistence.repositories.JPACustomerRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerJwtAuthorizationIntegrationTest {

    private static final String AUDIENCE = "fiap-api";
    private static final String KEY_ID = "customer-jwt-test-key";
    private static final KeyPair SIGNING_KEYS = generateKeyPair();
    private static final KeyPair INVALID_KEYS = generateKeyPair();
    private static HttpServer jwksServer;
    private static final String ISSUER = startJwksServer();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JPAServiceOrderRepository serviceOrderRepository;

    @Autowired
    private JPACustomerRepository customerRepository;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("JWT_ISSUER", () -> ISSUER);
        registry.add("JWT_AUDIENCE", () -> AUDIENCE);
    }

    @AfterAll
    static void stopJwksServer() {
        jwksServer.stop(0);
    }

    @Test
    @DisplayName("accepts an RS256 customer token and limits my-orders to its customerId")
    void acceptsLambdaShapedCustomerTokenAndUsesCustomerIdClaim() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();
        serviceOrderRepository.save(order(customerId));
        serviceOrderRepository.save(order(otherCustomerId));
        serviceOrderRepository.flush();

        mockMvc.perform(get("/service-orders/my-orders")
                        .header("Authorization", "Bearer " + token(
                                SIGNING_KEYS, ISSUER, AUDIENCE, "CUSTOMER", customerId,
                                Instant.now().plusSeconds(300))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].customerId").value(contains(customerId.toString())));
    }

    @Test
    @DisplayName("rejects requests without a token and tokens without the CUSTOMER role")
    void rejectsMissingTokenAndWrongRole() throws Exception {
        mockMvc.perform(get("/service-orders/my-orders"))
                .andExpect(status().isUnauthorized());

        UUID customerId = UUID.randomUUID();
        mockMvc.perform(get("/service-orders/my-orders")
                        .header("Authorization", "Bearer " + token(
                                SIGNING_KEYS, ISSUER, AUDIENCE, "USER", customerId,
                                Instant.now().plusSeconds(300))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("rejects expired, wrong issuer, wrong audience, and incorrectly signed JWTs")
    void rejectsInvalidJwtClaimsAndSignature() throws Exception {
        UUID customerId = UUID.randomUUID();
        expectUnauthorized(token(SIGNING_KEYS, ISSUER, AUDIENCE, "CUSTOMER", customerId,
                Instant.now().minusSeconds(60)));
        expectUnauthorized(token(SIGNING_KEYS, ISSUER + "/wrong", AUDIENCE, "CUSTOMER", customerId,
                Instant.now().plusSeconds(300)));
        expectUnauthorized(token(SIGNING_KEYS, ISSUER, "other-audience", "CUSTOMER", customerId,
                Instant.now().plusSeconds(300)));
        expectUnauthorized(token(INVALID_KEYS, ISSUER, AUDIENCE, "CUSTOMER", customerId,
                Instant.now().plusSeconds(300)));
    }

    @Test
    @DisplayName("prevents duplicate customer documents at the database boundary")
    void preventsDuplicateCustomerDocuments() {
        String cpf = "52998224725";
        customerRepository.saveAndFlush(customer("primeiro", cpf));

        assertThatThrownBy(() -> customerRepository.saveAndFlush(customer("segundo", cpf)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void expectUnauthorized(String token) throws Exception {
        mockMvc.perform(get("/service-orders/my-orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private static JPAServiceOrderEntity order(UUID customerId) {
        return new JPAServiceOrderEntity(
                UUID.randomUUID(), customerId, UUID.randomUUID(), "Consulta", null,
                ServiceOrderStatus.RECEIVED, java.time.LocalDateTime.now(), null, null, ServiceOrderPriority.LOW);
    }

    private static JPACustomerEntity customer(String name, String cpf) {
        return new JPACustomerEntity(UUID.randomUUID(), name, name + "@example.test", "11999998888", cpf);
    }

    private static String token(KeyPair keys, String issuer, String audience, String role,
                                UUID customerId, Instant expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(customerId.toString())
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(expiresAt))
                .claim("roles", List.of(role))
                .claim("customerId", customerId.toString())
                .build();

        SignedJWT signedJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), claims);
        signedJwt.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
        return signedJwt.serialize();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create test RSA keys", exception);
        }
    }

    private static String startJwksServer() {
        try {
            RSAKey publicJwk = new RSAKey.Builder((RSAPublicKey) SIGNING_KEYS.getPublic())
                    .keyID(KEY_ID)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
            byte[] jwks = new JWKSet(publicJwk).toString().getBytes(StandardCharsets.UTF_8);
            jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            jwksServer.createContext("/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                exchange.getResponseBody().write(jwks);
                exchange.close();
            });
            jwksServer.start();
            return "http://127.0.0.1:" + jwksServer.getAddress().getPort();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not start test JWKS server", exception);
        }
    }
}
