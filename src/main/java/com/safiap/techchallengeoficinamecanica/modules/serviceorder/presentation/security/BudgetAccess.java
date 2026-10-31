package com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.security;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.responses.ServiceOrderResponse;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.use_cases.GetServiceOrderByIdUseCase;
import com.safiap.techchallengeoficinamecanica.modules.shared.presentation.AuthenticatedCustomer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Authorizes the order before reading its budget, rendering details, or changing a decision. */
@Component
public class BudgetAccess {
    private final GetServiceOrderByIdUseCase serviceOrders;

    public BudgetAccess(GetServiceOrderByIdUseCase serviceOrders) {
        this.serviceOrders = serviceOrders;
    }

    public ServiceOrderResponse require(UUID serviceOrderId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw denied();
        }
        UUID customerId = null;
        if (AuthenticatedCustomer.isCustomer(authentication)) {
            if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
                throw denied();
            }
            try {
                String claim = jwt.getClaimAsString("customerId");
                if (claim == null) {
                    throw denied();
                }
                customerId = UUID.fromString(claim);
            } catch (IllegalArgumentException | ClassCastException error) {
                throw denied();
            }
        } else if (authentication.getAuthorities().stream().noneMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority()) || "ROLE_USER".equals(authority.getAuthority()))) {
            throw denied();
        }
        ServiceOrderResponse order = serviceOrders.execute(serviceOrderId);
        if (customerId != null && !customerId.equals(order.customerId())) {
            throw denied();
        }
        return order;
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Access to this service order is not permitted");
    }
}
