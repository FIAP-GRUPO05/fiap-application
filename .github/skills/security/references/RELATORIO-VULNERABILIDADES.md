# Relatório de Análise de Vulnerabilidades — OWASP Top 10

> Tech Challenge Fase 1 — Oficina Mecânica (backend Spring Boot / Java 21).
> Data: 2026-06-30. Escopo: `oficina-backend` (raiz do repositório).

Este documento cumpre o entregável de **análise de vulnerabilidades** exigido no
guia do projeto. Ele tem duas partes complementares:

1. **Scan automatizado de dependências** (OWASP Dependency-Check) — evidência
   objetiva para o risco **A06 (componentes vulneráveis/desatualizados)**.
2. **Mapeamento manual OWASP Top 10 (2021)** — cada categoria confrontada com a
   aplicação, com status e ação recomendada.

---

## 1. Scan automatizado — OWASP Dependency-Check

Ferramenta: plugin oficial `org.owasp.dependencycheck` (Gradle), configurado em
`build.gradle`. Ele cruza cada dependência do projeto com a base de CVEs da NVD
(National Vulnerability Database) e gera um relatório com os CVEs encontrados,
sua severidade (CVSS) e a versão que corrige.

**Pré-requisito (ambiente Windows):** o build exige **JDK 21** e não roda se
`JAVA_HOME` não estiver definido (`gradlew.bat` falha com exit 9; `./gradlew` no
bash pode sair silenciosamente sem executar). Defina a JDK antes de rodar:

```powershell
# PowerShell — ajuste o caminho para a sua JDK 21
$env:JAVA_HOME = "C:\Users\<user>\.jdks\ms-21.0.7"
```

> Dica: ao rodar no terminal, **não** encadeie a saída em `| tail` — o pipe
> mascara o código de saída real do Gradle (retorna o do `tail`).

**Como rodar:**

```powershell
# (opcional, recomendado) chave da NVD acelera muito o download da base de CVEs
$env:NVD_API_KEY = "<sua-chave>"        # https://nvd.nist.gov/developers/request-an-api-key

.\gradlew.bat dependencyCheckAnalyze --console=plain
```

**Saída:** `build/reports/dependency-check-report.html` (+ `.json`). Anexe o HTML
ao documento de entrega — é a evidência do scan.

> A primeira execução **sem `NVD_API_KEY`** baixa a base de CVEs da NVD inteira e
> pode levar bastante tempo (o próprio Gradle avisa: *"the update can take a VERY
> long time without an API Key"*). Com a chave, cai para poucos minutos.

Notas de configuração (`build.gradle` → bloco `dependencyCheck`):

- Formatos `HTML` + `JSON`.
- `failBuildOnCVSS = 11` (report-only) para não quebrar o CI verde. Para tornar
  o scan um **gate de qualidade**, troque para `7.0` (falha em achados
  High/Critical).
- `suppressionFile = config/dependency-check-suppressions.xml` — para registrar
  falsos-positivos com justificativa (nada suprimido por padrão).

---

## 2. Mapeamento OWASP Top 10 (2021) → aplicação

Legenda de status: ✅ mitigado · 🟡 parcial / a reforçar · ⚪ não aplicável.

| # | Categoria | Status | Situação na aplicação | Ação recomendada |
|---|-----------|:---:|-----------------------|------------------|
| **A01** | Broken Access Control | 🟡 | Spring Security com `anyRequest().authenticated()`; endpoints públicos explícitos (`/auth/**`, health, swagger); `@EnableMethodSecurity` ativo; papéis via claim `roles` do JWT. | Aplicar `@PreAuthorize` nas operações sensíveis (ex.: só mecânico atualiza status); testar autorização por papel. |
| **A02** | Cryptographic Failures | 🟡 | Senhas com **BCrypt** (`BCryptPasswordEncoder`); JWT assinado HS256. | **Remover o default de `JWT_SECRET`** no compose; exigir segredo forte provisionado por ambiente (falhar no boot se ausente). |
| **A03** | Injection | ✅ | Persistência via **Spring Data JPA/Hibernate** (queries parametrizadas); entrada validada com Bean Validation (`spring-boot-starter-validation`). | Manter `@Valid` em todo DTO novo; evitar JPQL/SQL concatenado. |
| **A04** | Insecure Design | ✅ | Modelagem DDD; value objects com invariantes (`CnpjCpf` com dígito verificador, placa Mercosul); máquina de estados da OS (6 estados) impede transições inválidas. | Manter regras de negócio no domínio; revisar casos de abuso a cada novo fluxo. |
| **A05** | Security Misconfiguration | 🟡 | Actuator expõe **apenas** `health` com `show-details=never`; CSRF desabilitado (correto p/ API stateless com JWT); sessão STATELESS. | Trocar credenciais default do banco (`oficina/oficina_pass`) por segredo por ambiente; revisar headers de segurança. |
| **A06** | Vulnerable & Outdated Components | 🟡 | **Dependency-Check configurado** (ver Parte 1). Pendente: anexar o relatório do scan e revisar achados. | Rodar `dependencyCheckAnalyze`, tratar achados High/Critical, atualizar versões. |
| **A07** | Identification & Auth Failures | 🟡 | JWT **stateless** (Resource Server), BCrypt, sessão sem estado. | Mesma dívida do A02: fortalecer/rotacionar `JWT_SECRET`; considerar expiração/refresh e lockout. |
| **A08** | Software & Data Integrity Failures | 🟡 | CI/CD gitflow com branch-policy; **Dockerfile multi-stage non-root**. | Verificar integridade de dependências (checksums); não confiar em artefatos não assinados no pipeline. |
| **A09** | Security Logging & Monitoring | 🟡 | Observabilidade via New Relic (APM, logs JSON e métricas no EKS); healthcheck de container. | Adicionar log de eventos de segurança (login falho, acesso negado) sem vazar dados sensíveis. |
| **A10** | Server-Side Request Forgery (SSRF) | ⚪ | Sem chamadas de saída controladas por input do usuário. | Reavaliar se surgirem integrações externas / webhooks. |

---

## 3. Achados priorizados (dívida a fechar)

Consolidado da auditoria de segurança (`agentico/conhecimento/auditorias/security.md`):

| Prioridade | Achado | OWASP | Severidade |
|:---:|--------|:---:|:---:|
| 1 | `JWT_SECRET` com default hardcoded no `docker-compose.yaml` | A02 / A07 | Alta |
| 2 | Credenciais de banco com defaults previsíveis (`oficina_pass`) | A05 | Média |
| 3 | Autorização fina (`@PreAuthorize`) ausente em operações sensíveis | A01 | Média |
| 4 | Relatório de scan de dependências ainda não anexado à entrega | A06 | — |

**Padrão a seguir:** segredos nunca versionados nem com default em produção —
sempre via env/secret manager, com a aplicação falhando no boot se faltarem.
Autenticação documentada com implementação correspondente. Todo input de borda
validado com Bean Validation. Dependências auditadas antes de cada release.
