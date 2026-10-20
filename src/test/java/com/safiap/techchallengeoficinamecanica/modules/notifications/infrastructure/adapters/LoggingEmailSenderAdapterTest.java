package com.safiap.techchallengeoficinamecanica.modules.notifications.infrastructure.adapters;

import com.safiap.techchallengeoficinamecanica.modules.notifications.domain.value_objects.EmailMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class LoggingEmailSenderAdapterTest {
    @Test
    void acceptsAMessageWhenEmailSendingIsDisabled() {
        assertThatCode(() -> new LoggingEmailSenderAdapter().send(
                new EmailMessage("client@example.test", "OS", "Pronta", false)))
                .doesNotThrowAnyException();
    }
}
