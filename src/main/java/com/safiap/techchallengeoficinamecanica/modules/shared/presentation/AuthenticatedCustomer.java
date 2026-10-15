package com.safiap.techchallengeoficinamecanica.modules.shared.presentation;

import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.AuthException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Leitura do cliente por tras do token. O claim {@code customerId} so e emitido para usuarios
 * ligados a um cadastro de cliente — um token de oficina nao o carrega.
 */
public final class AuthenticatedCustomer {

    private static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
    private static final String CUSTOMER_ID_CLAIM = "customerId";

    private AuthenticatedCustomer() {
    }

    public static boolean isCustomer(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ROLE_CUSTOMER.equals(authority.getAuthority()));
    }

    public static UUID requireId(Authentication authentication) {
        return requireId((Jwt) authentication.getPrincipal());
    }

    public static UUID requireId(Jwt token) {
        String customerId = token.getClaimAsString(CUSTOMER_ID_CLAIM);
        if (customerId == null) {
            throw new AuthException("Authenticated user is not linked to a customer");
        }
        return UUID.fromString(customerId);
    }
}
