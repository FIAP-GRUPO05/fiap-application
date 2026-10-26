package com.safiap.techchallengeoficinamecanica.modules.notifications.infrastructure.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safiap.techchallengeoficinamecanica.modules.notifications.application.ports.EmailSenderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailSenderAdapter {

    @Value("${notifications.email}")
    public String notificacao;

    @Bean
    public EmailSenderPort createEmailSender(JavaMailSender mailSender,
                                             ObjectMapper objectMapper,
                                             @Value("${notifications.email.from}") String from,
                                             @Value("${notifications.lambda.function-name}") String function,
                                             @Value("${notifications.lambda.region}") String region,
                                             @Value("${notifications.lambda.endpoint:}") String endpoint){
        switch (notificacao){
            case "DEFAULT": return new SmtpEmailSenderAdapter(mailSender, from);
            case "LAMBDA":
                var builder = software.amazon.awssdk.services.lambda.LambdaClient.builder()
                        .region(software.amazon.awssdk.regions.Region.of(region))
                        .overrideConfiguration(software.amazon.awssdk.core.client.config.ClientOverrideConfiguration.builder()
                                .apiCallTimeout(java.time.Duration.ofSeconds(40))
                                .apiCallAttemptTimeout(java.time.Duration.ofSeconds(35)).build());
                if (!endpoint.isBlank()) builder.endpointOverride(java.net.URI.create(endpoint));
                return new LambdaEmailSenderAdapter(objectMapper, builder.build(), function);
            case "NONE" : return new LoggingEmailSenderAdapter();
            default: throw new RuntimeException("Invalid propertie value");
        }
    }
}
