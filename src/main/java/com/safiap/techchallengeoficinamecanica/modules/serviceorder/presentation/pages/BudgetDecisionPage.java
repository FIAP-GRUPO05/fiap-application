package com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.pages;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.responses.BudgetResponse;
import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.responses.ServiceOrderResponse;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Páginas devolvidas ao cliente no fluxo de decisão do orçamento pelo link do e-mail.
 * São o único retorno que ele vê — o navegador abre direto do webmail, sem front-end.
 */
public final class BudgetDecisionPage {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private BudgetDecisionPage() {
    }

    /**
     * Tela que o link do e-mail abre. É só leitura: a decisão sai daqui por POST, nunca pelo
     * GET do link — assim um pré-carregamento do webmail não aprova orçamento nenhum.
     */
    public static String pending(ServiceOrderResponse serviceOrder, BudgetResponse budget, String highlight) {
        return render(serviceOrder, budget, "Orçamento aguardando sua resposta",
                "#0b5cad", "&#128203;",
                "Confira os itens abaixo e responda. Sua resposta só é registrada quando você "
                        + "clica em um dos botões desta página.",
                decisionForms(serviceOrder.serviceOrderId(), highlight));
    }

    public static String approved(ServiceOrderResponse serviceOrder, BudgetResponse budget) {
        return render(serviceOrder, budget, "Orçamento aprovado",
                "#1b7f4b", "&#10004;",
                "Recebemos sua aprovação. Vamos iniciar o serviço no seu veículo e avisaremos "
                        + "por e-mail a cada mudança de status.",
                "");
    }

    public static String rejected(ServiceOrderResponse serviceOrder, BudgetResponse budget) {
        return render(serviceOrder, budget, "Orçamento recusado",
                "#b3261e", "&#10006;",
                "Registramos sua recusa e a ordem de serviço foi cancelada. Nenhum serviço será "
                        + "executado e nada será cobrado. Se mudar de ideia, fale com a oficina.",
                "");
    }

    /** Decisão oposta a uma que já foi registrada: não dá para desfazer por aqui. */
    public static String conflict(ServiceOrderResponse serviceOrder, BudgetResponse budget) {
        return render(serviceOrder, budget, "Este orçamento já foi respondido",
                "#8a6d00", "&#9888;",
                "Já existe uma resposta registrada para este orçamento e ela não pode ser trocada "
                        + "por esta página. Se precisar mudar a decisão, fale com a oficina.",
                "");
    }

    /**
     * Um <form> por decisão: o botão faz POST, que é o método certo para uma ação que muda
     * estado, e o destaque só reflete o link que o cliente clicou no e-mail.
     */
    private static String decisionForms(UUID serviceOrderId, String highlight) {
        boolean approveIsPrimary = !"reject".equals(highlight);
        return """
                        <div class="actions">
                %s
                %s
                        </div>
                """.formatted(
                decisionForm(serviceOrderId, "approve", "Aprovar orçamento", approveIsPrimary),
                decisionForm(serviceOrderId, "reject", "Recusar orçamento", !approveIsPrimary));
    }

    private static String decisionForm(UUID serviceOrderId, String action, String label, boolean primary) {
        return """
                          <form method="post" action="/service-orders/%s/budget/%s">
                            <button type="submit" class="btn %s">%s</button>
                          </form>""".formatted(
                serviceOrderId, action, primary ? "primary" : "secondary", label);
    }

    private static String render(ServiceOrderResponse serviceOrder,
                                 BudgetResponse budget,
                                 String title,
                                 String accent,
                                 String icon,
                                 String message,
                                 String actions) {
        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                  <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>%s — Oficina Mecânica</title>
                    <style>
                      :root { color-scheme: light; }
                      body { margin: 0; padding: 24px; background: #f4f5f7;
                             font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif;
                             color: #1f2328; }
                      .card { max-width: 640px; margin: 0 auto; background: #fff; border-radius: 12px;
                              box-shadow: 0 1px 3px rgba(0,0,0,.12); overflow: hidden; }
                      .head { background: %s; color: #fff; padding: 24px; }
                      .head h1 { margin: 8px 0 0; font-size: 20px; font-weight: 600; }
                      .icon { font-size: 32px; line-height: 1; }
                      .body { padding: 24px; }
                      .msg { margin: 0 0 20px; line-height: 1.5; }
                      dl { display: grid; grid-template-columns: auto 1fr; gap: 8px 16px; margin: 0 0 20px; }
                      dt { color: #59636e; }
                      dd { margin: 0; font-weight: 600; }
                      table { width: 100%%; border-collapse: collapse; font-size: 14px; }
                      th, td { text-align: left; padding: 8px; border-bottom: 1px solid #e6e8eb; }
                      th { color: #59636e; font-weight: 600; }
                      td.num, th.num { text-align: right; }
                      tfoot td { border-bottom: none; font-size: 16px; font-weight: 700; padding-top: 12px; }
                      .foot { padding: 16px 24px; background: #fafbfc; color: #59636e; font-size: 13px;
                              border-top: 1px solid #e6e8eb; }
                      .actions { display: flex; flex-wrap: wrap; gap: 12px; margin: 24px 0 4px; }
                      .btn { font: inherit; font-weight: 600; padding: 12px 20px; border-radius: 8px;
                             border: 1px solid transparent; cursor: pointer; }
                      .btn.primary { background: #1b7f4b; color: #fff; }
                      .btn.secondary { background: #fff; color: #b3261e; border-color: #b3261e; }
                    </style>
                  </head>
                  <body>
                    <div class="card">
                      <div class="head">
                        <div class="icon">%s</div>
                        <h1>%s</h1>
                      </div>
                      <div class="body">
                        <p class="msg">%s</p>
                        <dl>
                          <dt>Ordem de serviço</dt><dd>#%s</dd>
                          <dt>Situação da OS</dt><dd>%s</dd>
                          <dt>Abertura</dt><dd>%s</dd>
                          <dt>Problema relatado</dt><dd>%s</dd>
                          <dt>Diagnóstico</dt><dd>%s</dd>
                        </dl>
                        <table>
                          <thead>
                            <tr>
                              <th>Item</th><th>Tipo</th><th class="num">Qtd.</th>
                              <th class="num">Unitário</th><th class="num">Total</th>
                            </tr>
                          </thead>
                          <tbody>
                %s
                          </tbody>
                          <tfoot>
                            <tr><td colspan="4">Total do orçamento</td><td class="num">%s</td></tr>
                          </tfoot>
                        </table>
                %s
                      </div>
                      <div class="foot">
                        Esta é uma confirmação automática, não é necessário respondê-la.<br>Oficina Mecânica
                      </div>
                    </div>
                  </body>
                </html>
                """.formatted(
                escape(title), accent, icon, escape(title), escape(message),
                shortId(serviceOrder), statusLabel(serviceOrder), openedAt(serviceOrder),
                escape(orDash(serviceOrder.problemDescription())),
                escape(orDash(serviceOrder.diagnosis())),
                itemRows(budget), money(budget.totalAmount()), actions);
    }

    private static String itemRows(BudgetResponse budget) {
        if (budget.items() == null || budget.items().isEmpty()) {
            return "            <tr><td colspan=\"5\">Nenhum item no orçamento.</td></tr>";
        }
        StringBuilder rows = new StringBuilder();
        for (BudgetResponse.BudgetItemResponse item : budget.items()) {
            rows.append("""
                                <tr>
                                  <td>%s</td><td>%s</td><td class="num">%d</td>
                                  <td class="num">%s</td><td class="num">%s</td>
                                </tr>
                    """.formatted(
                    escape(item.description()),
                    "PART".equals(item.type()) ? "Peça" : "Serviço",
                    item.quantity(), money(item.unitPrice()), money(item.totalPrice())));
        }
        return rows.toString();
    }

    private static String shortId(ServiceOrderResponse serviceOrder) {
        return serviceOrder.serviceOrderId().toString().substring(0, 8);
    }

    private static String openedAt(ServiceOrderResponse serviceOrder) {
        return serviceOrder.openedAt() == null ? "—" : serviceOrder.openedAt().format(DATE_TIME);
    }

    private static String statusLabel(ServiceOrderResponse serviceOrder) {
        return switch (serviceOrder.status()) {
            case RECEIVED -> "Recebida";
            case IN_DIAGNOSIS -> "Em diagnóstico";
            case AWAITING_APPROVAL -> "Aguardando aprovação";
            case CANCELED -> "Cancelada";
            case IN_EXECUTION -> "Em execução";
            case FINALIZED -> "Concluída";
            case DELIVERED -> "Veículo entregue";
        };
    }

    private static String money(BigDecimal value) {
        if (value == null) {
            return "R$ 0,00";
        }
        return "R$ " + value.setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString().replace('.', ',');
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    /** Descrições vêm do cadastro: escapar evita que um item quebre a página. */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
