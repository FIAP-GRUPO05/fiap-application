package com.safiap.techchallengeoficinamecanica.modules.notifications.infrastructure.adapters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safiap.techchallengeoficinamecanica.modules.notifications.application.ports.EmailSenderPort;
import com.safiap.techchallengeoficinamecanica.modules.notifications.domain.value_objects.EmailMessage;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;


public class LambdaEmailSenderAdapter implements EmailSenderPort {

    private final LambdaClient lambdaClient = LambdaClient.builder().build();
    private final ObjectMapper objectMapper;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public LambdaEmailSenderAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(EmailMessage message) {

        InvokeRequest invokeRequest = null;
        try {
            invokeRequest = InvokeRequest.builder()
                    .functionName("lambda-email")
                    .invocationType("RequestResponse")
                    .payload(SdkBytes.fromUtf8String(objectMapper.writeValueAsString(message)))
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        InvokeResponse response = lambdaClient.invoke(invokeRequest);
        log.info("Status code {}",response.statusCode());
        log.info("Body {}",response.payload().asUtf8String());

    }
}
