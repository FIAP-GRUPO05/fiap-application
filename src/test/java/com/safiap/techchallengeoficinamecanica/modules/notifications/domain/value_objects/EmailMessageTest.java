package com.safiap.techchallengeoficinamecanica.modules.notifications.domain.value_objects;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.value_objects.ServiceOrderStatus;
import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailMessageTest {

    private static final String BASE_URL = "http://localhost:8080";

    private static final UUID SERVICE_ORDER_ID = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
    private static final NotificationRecipient RECIPIENT =
            new NotificationRecipient("João Silva", "joao@email.com");

    @Test
    @DisplayName("builds the status change email with recipient, short id and vehicle")
    void buildsStatusChangeEmail() {
        EmailMessage message = EmailMessage.serviceOrderStatusChanged(
                RECIPIENT, SERVICE_ORDER_ID, "Honda Civic (ABC1D23)", ServiceOrderStatus.RECEIVED, BASE_URL);

        assertThat(message.to()).isEqualTo("joao@email.com");
        assertThat(message.subject()).isEqualTo("Ordem de serviço #3f2504e0 — recebida");
        assertThat(message.body())
                .contains("Olá, João Silva!")
                .contains("Sua ordem de serviço foi recebida.")
                .contains("Ordem de serviço: #3f2504e0")
                .contains("Veículo: Honda Civic (ABC1D23)");
    }

    @ParameterizedTest
    @CsvSource({
            "RECEIVED,          recebida,                          Sua ordem de serviço foi recebida.",
            "IN_DIAGNOSIS,      em diagnóstico,                    Iniciamos o diagnóstico do seu veículo.",
            "AWAITING_APPROVAL, orçamento aguardando aprovação,    Seu orçamento está pronto e aguarda aprovação.",
            "IN_EXECUTION,      em execução,                       O serviço no seu veículo foi iniciado.",
            "FINALIZED,         concluída,                         O serviço foi concluído e seu veículo está pronto.",
            "DELIVERED,         veículo entregue,                  Seu veículo foi entregue. Obrigado pela preferência!"
    })
    @DisplayName("uses a specific subject and message for each status")
    void usesSpecificTextPerStatus(ServiceOrderStatus status, String subjectSuffix, String expectedMessage) {
        EmailMessage message = EmailMessage.serviceOrderStatusChanged(
                RECIPIENT, SERVICE_ORDER_ID, "Honda Civic (ABC1D23)", status, BASE_URL);

        assertThat(message.subject()).isEqualTo("Ordem de serviço #3f2504e0 — " + subjectSuffix);
        assertThat(message.body()).contains(expectedMessage);
    }

    @ParameterizedTest
    @EnumSource(ServiceOrderStatus.class)
    @DisplayName("never produces a blank subject or body for any status")
    void neverProducesBlankContent(ServiceOrderStatus status) {
        EmailMessage message = EmailMessage.serviceOrderStatusChanged(
                RECIPIENT, SERVICE_ORDER_ID, "não informado", status, BASE_URL);

        assertThat(message.subject()).isNotBlank();
        assertThat(message.body()).isNotBlank();
    }

    @Test
    @DisplayName("rejects blank recipient, subject or body")
    void rejectsBlankFields() {
        assertThatThrownBy(() -> new EmailMessage(" ", "assunto", "corpo", false))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new EmailMessage("joao@email.com", null, "corpo", false))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new EmailMessage("joao@email.com", "assunto", "", false))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("rejects a null service order id")
    void rejectsNullServiceOrderId() {
        assertThatThrownBy(() -> EmailMessage.serviceOrderStatusChanged(
                RECIPIENT, null, "Honda Civic (ABC1D23)", ServiceOrderStatus.RECEIVED, BASE_URL))
                .isInstanceOf(DomainException.class);
    }
    // --- links de aprovacao: montados sobre a URL publica configurada ---

    @Test
    @DisplayName("builds the approval email as HTML with the two action links")
    void buildsApprovalLinks() {
        EmailMessage message = EmailMessage.serviceOrderStatusChanged(
                RECIPIENT, SERVICE_ORDER_ID, "Honda Civic (ABC1D23)",
                ServiceOrderStatus.AWAITING_APPROVAL, BASE_URL);

        assertThat(message.html()).isTrue();
        assertThat(message.body())
                .contains(BASE_URL + "/service-orders/" + SERVICE_ORDER_ID + "/budget/decision?decision=approve")
                .contains(BASE_URL + "/service-orders/" + SERVICE_ORDER_ID + "/budget/decision?decision=reject");
    }

    @Test
    @DisplayName("uses the configured public URL, not a hardcoded host")
    void usesConfiguredPublicUrl() {
        String prodUrl = "http://a1b2c3.us-east-1.elb.amazonaws.com:8080";

        EmailMessage message = EmailMessage.serviceOrderStatusChanged(
                RECIPIENT, SERVICE_ORDER_ID, "Honda Civic (ABC1D23)",
                ServiceOrderStatus.AWAITING_APPROVAL, prodUrl);

        assertThat(message.body()).contains(prodUrl).doesNotContain("localhost");
    }

    @Test
    @DisplayName("trims a trailing slash so the links never get a double slash")
    void normalizesTrailingSlash() {
        EmailMessage message = EmailMessage.serviceOrderStatusChanged(
                RECIPIENT, SERVICE_ORDER_ID, "Honda Civic (ABC1D23)",
                ServiceOrderStatus.AWAITING_APPROVAL, "http://oficina.local/");

        assertThat(message.body())
                .contains("http://oficina.local/service-orders/")
                .doesNotContain("http://oficina.local//");
    }

    @Test
    @DisplayName("refuses to build the approval email without a public URL")
    void failsWithoutPublicUrl() {
        assertThatThrownBy(() -> EmailMessage.serviceOrderStatusChanged(
                RECIPIENT, SERVICE_ORDER_ID, "Honda Civic (ABC1D23)",
                ServiceOrderStatus.AWAITING_APPROVAL, ""))
                .isInstanceOf(DomainException.class);

        assertThatThrownBy(() -> EmailMessage.serviceOrderStatusChanged(
                RECIPIENT, SERVICE_ORDER_ID, "Honda Civic (ABC1D23)",
                ServiceOrderStatus.AWAITING_APPROVAL, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("other statuses stay plain text and carry no links")
    void otherStatusesHaveNoLinks() {
        EmailMessage message = EmailMessage.serviceOrderStatusChanged(
                RECIPIENT, SERVICE_ORDER_ID, "Honda Civic (ABC1D23)",
                ServiceOrderStatus.RECEIVED, BASE_URL);

        assertThat(message.html()).isFalse();
        assertThat(message.body()).doesNotContain("<a href");
    }

    @Test
    @DisplayName("each action link carries its own decision, and neither of them decides by itself")
    void actionLinksPointToTheRightEndpoint() {
        EmailMessage message = EmailMessage.serviceOrderStatusChanged(
                RECIPIENT, SERVICE_ORDER_ID, "Honda Civic (ABC1D23)",
                ServiceOrderStatus.AWAITING_APPROVAL, BASE_URL);

        String decision = BASE_URL + "/service-orders/" + SERVICE_ORDER_ID + "/budget/decision";
        // Trava o alinhamento dos %s: ja houve um bug em que "Rejeitar" apontava para o approve.
        assertThat(message.body())
                .contains("<a href=\"" + decision + "?decision=approve\">Aceitar</a>")
                .contains("<a href=\"" + decision + "?decision=reject\">Rejeitar</a>");
        // O link nunca pode apontar para o endpoint que muda estado: la so se chega por POST.
        assertThat(message.body())
                .doesNotContain("href=\"" + BASE_URL + "/service-orders/" + SERVICE_ORDER_ID + "/budget/approve\"")
                .doesNotContain("href=\"" + BASE_URL + "/service-orders/" + SERVICE_ORDER_ID + "/budget/reject\"");
    }

}
