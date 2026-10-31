package com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.security;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.GetServiceOrderByIdUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BudgetAccessTest {
    @Test
    void malformedOrUnauthenticatedPrincipalsAreDeniedBeforeAnyOrderLookup() {
        var orders = mock(GetServiceOrderByIdUseCase.class);
        var access = new BudgetAccess(orders);
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> access.require(id, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> access.require(id, new UsernamePasswordAuthenticationToken("user", "unused")))
                .isInstanceOf(AccessDeniedException.class);
        for (String role : new String[]{"ROLE_UNKNOWN", "ROLE_CUSTOMER"}) {
            var principal = new UsernamePasswordAuthenticationToken("not-a-jwt", "unused", AuthorityUtils.createAuthorityList(role));
            assertThatThrownBy(() -> access.require(id, principal)).isInstanceOf(AccessDeniedException.class);
        }
        for (Object claim : new Object[]{"invalid", Map.of("unexpected", "type")}) {
            var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject("customer").claim("customerId", claim).build();
            var principal = new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
            assertThatThrownBy(() -> access.require(id, principal)).isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(orders);
    }
}
