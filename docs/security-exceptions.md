# Excecoes de seguranca avaliadas

## CVE-2026-47884 - XsltView

O Trivy classifica este aviso como HIGH. O fornecedor classifica como MEDIUM e
informa que a exploracao exige `XsltView` e um mapeamento `/**` que resulte em
renderizacao de views sem nome explicito.

Esta API usa controladores REST. O unico `@Controller` proprio faz um redirect
explicito de `/` para o Swagger. Nao ha `XsltView` configurada nem mapeamento
curinga que renderize views. Por isso, a configuracao da aplicacao nao atende
as condicoes de exploracao do aviso.

O teste `applicationDoesNotEnableXsltViewsOrWildcardViewRendering` verifica essas
condicoes no contexto Spring real durante a esteira. A excecao em `.trivyignore`
se restringe a este CVE e deve ser reavaliada antes de adicionar views MVC/XSLT
ou mudar a versao principal do Spring. Os demais achados HIGH/CRITICAL seguem
bloqueando a promocao.

Referencia primaria: [aviso de seguranca do Spring](https://spring.io/security/cve-2026-47884/).

## CVE-2026-47890 - fragmentos de views em SSE

O Trivy classifica como HIGH; o fornecedor classifica como LOW. A exploracao
exige o envio de fragmentos de views por Server-Sent Events com dados controlados
por um atacante. Esta API nao publica streams SSE nem usa fragmentos de views.

O teste `applicationDoesNotPublishSseOrViewFragments` verifica os tipos de retorno
dos handlers e seus media types no contexto real. A excecao se restringe a este
CVE e deve ser removida ou reavaliada antes de adicionar streaming ou views.
As duas excecoes foram avaliadas em 18/10/2026; a atualizacao principal do Spring
tambem exige revisao delas. Elas nao dispensam a correcao dos demais avisos.

Referencia primaria: [aviso de seguranca do Spring](https://spring.io/security/cve-2026-47890/).
