package com.safiap.techchallengeoficinamecanica.modules.shared.presentation;

import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.AuthException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedCustomerTest {
    @Test
    void acceptsOnlyCustomerRoleAndReadsCustomerId() {
        UUID id = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("customerId", id.toString()).build();
        var customer = new TestingAuthenticationToken(jwt, null, "ROLE_CUSTOMER");
        var admin = new TestingAuthenticationToken(jwt, null, "ROLE_ADMIN");
        assertThat(AuthenticatedCustomer.isCustomer(customer)).isTrue();
        assertThat(AuthenticatedCustomer.isCustomer(admin)).isFalse();
        assertThat(AuthenticatedCustomer.requireId(customer)).isEqualTo(id);
    }

    @Test
    void rejectsJwtWithoutCustomerLink() {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        assertThatThrownBy(() -> AuthenticatedCustomer.requireId(jwt)).isInstanceOf(AuthException.class);
    }
}
