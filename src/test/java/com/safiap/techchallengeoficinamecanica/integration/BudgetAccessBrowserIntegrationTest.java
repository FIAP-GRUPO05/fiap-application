package com.safiap.techchallengeoficinamecanica.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.*;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.infrastructure.persistence.entities.*;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.infrastructure.persistence.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in local Chrome/Edge flow; the test gateway mocks only the external CPF issuer. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:budget_browser;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=YEAR", "notifications.email=NONE"})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "FIAP_BROWSER_TEST", matches = "true")
class BudgetAccessBrowserIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private JwtEncoder encoder;
    @Autowired private JPAServiceOrderRepository orders;
    @Autowired private JPABudgetRepository budgets;
    @Autowired private JPABudgetItemRepository items;
    @Autowired private ObjectMapper mapper;

    @Test
    void emailLinkAuthenticatesCpfAndUsesJwtForTheOwnersBudgetInRealBrowser() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        UUID budget = UUID.randomUUID();
        orders.saveAndFlush(new JPAServiceOrderEntity(order, owner, UUID.randomUUID(), "Browser private problem",
                "Browser private diagnosis", ServiceOrderStatus.AWAITING_APPROVAL, LocalDateTime.now(), null, null, ServiceOrderPriority.LOW));
        budgets.saveAndFlush(new JPABudgetEntity(budget, order, BudgetStatus.FINALIZED, LocalDateTime.now()));
        items.saveAndFlush(new JPABudgetItemEntity(UUID.randomUUID(), budget, BudgetItemType.SERVICE,
                UUID.randomUUID(), "Browser service", 1, new BigDecimal("200.00"), null));
        Path directory = Files.createDirectories(Path.of("build", "browser-test"));
        Path fixture = Files.createTempFile(directory, "fixture-", ".json");
        try {
            Files.writeString(fixture, mapper.writeValueAsString(Map.of("orderId", order.toString(),
                    "ownerCpf", "52998224725", "foreignCpf", "11144477735",
                    "ownerToken", token(owner), "foreignToken", token(UUID.randomUUID()))));
            Process process = new ProcessBuilder("node", "scripts/budget-access-browser-test.mjs", Integer.toString(port), fixture.toAbsolutePath().toString())
                    .redirectErrorStream(true).start();
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly();
            assertThat(finished).as("Browser regression timeout").isTrue();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            System.out.println(output);
            assertThat(process.exitValue()).as(output).isZero();
        } finally {
            Files.deleteIfExists(fixture);
        }
    }

    private String token(UUID customer) {
        var claims = JwtClaimsSet.builder().subject("browser-test@example.test").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300)).claim("roles", List.of("CUSTOMER"))
                .claim("customerId", customer.toString()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
