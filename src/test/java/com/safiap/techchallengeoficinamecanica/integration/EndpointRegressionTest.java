package com.safiap.techchallengeoficinamecanica.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndpointRegressionTest {
    @Autowired MockMvc mockMvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    @Test
    void everyDeclaredApiRouteCanBeReached() throws Exception {
        int checked = 0;
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            if (!entry.getValue().getBeanType().getName().startsWith("com.safiap.")) continue;
            RequestMappingInfo info = entry.getKey();
            Set<String> paths = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().getPatternValues()
                    : info.getPatternsCondition().getPatterns();
            var methods = info.getMethodsCondition().getMethods();
            for (String path : paths) {
                String concrete = path.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
                for (var method : methods) {
                    var result = mockMvc.perform(MockMvcRequestBuilders.request(HttpMethod.valueOf(method.name()), concrete)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                            .andReturn();
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("%s %s must not reject its method", method, path)
                            .isNotEqualTo(405);
                    if (status == 404) {
                        assertThat(result.getRequest().getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE))
                                .as("%s %s returned a domain 404 after matching a handler", method, path)
                                .isNotNull();
                    }
                    checked++;
                }
            }
        }
        assertThat(checked).as("API routes covered by regression smoke").isGreaterThan(0);
        System.out.println("Endpoint reachability checks: " + checked);
    }
}
