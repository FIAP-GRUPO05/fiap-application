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
                                             @Value("${notifications.email.from}") String from){
        switch (notificacao){
            case "DEFAULT": return new SmtpEmailSenderAdapter(mailSender, from);
            case "LAMBDA":  return new LambdaEmailSenderAdapter(objectMapper);
            case "NONE" : return new LoggingEmailSenderAdapter();
            default: throw new RuntimeException("Invalid propertie value");
        }
    }
}
