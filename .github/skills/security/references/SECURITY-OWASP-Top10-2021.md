# Avaliação de Segurança — OWASP Top 10 2021

**Projeto:** `tech-challenge-oficina-mecanica`
**Stack:** Spring Boot 3.2.0 · Java 21 · arquitetura hexagonal/DDD (módulos `auth`, `register`, `inventory`, `serviceorder`, `notifications`, `shared`)
**Data da avaliação:** 2026-06-30
**Escopo:** análise estática do código-fonte, configuração (`application.properties`, `docker-compose*.yaml`, `Dockerfile`), migrações Flyway e dependências (`build.gradle`).

> **Contexto:** projeto acadêmico (Tech Challenge FIAP). As severidades estão calibradas para "o que impediria uma ida a produção com segurança". A base é sólida — BCrypt, queries parametrizadas, JWT stateless, container não-root, actuator restrito. Os problemas encontrados são majoritariamente de *hardening* e de design de autenticação.

---

## Placar geral

| #   | Categoria                        | Veredito                          | Severidade |
|-----|----------------------------------|-----------------------------------|:----------:|
| A01 | Broken Access Control            | Falha parcial                     | 🟠 Média   |
| A02 | Cryptographic Failures           | Falha parcial                     | 🟠 Média   |
| A03 | Injection                        | **Conforme**                      | 🟢 Baixa   |
| A04 | Insecure Design                  | Falha (sem anti-brute-force)      | 🔴 Alta    |
| A05 | Security Misconfiguration        | Falha parcial                     | 🟠 Média   |
| A06 | Vulnerable & Outdated Components  | Falha                             | 🟠 Média   |
| A07 | Identification & Auth Failures   | Falha (sem política de senha)     | 🔴 Alta    |
| A08 | Software & Data Integrity        | **Conforme**                      | 🟢 Baixa   |
| A09 | Logging & Monitoring Failures    | Falha parcial                     | 🟠 Média   |
| A10 | SSRF                             | **Conforme / N.A.**               | 🟢 Baixa   |

---

## 🔴 A04 — Insecure Design · **Alta**

**Sem proteção contra força bruta.** `/auth/login` e `/auth/register` são `permitAll()` (`SecurityConfig.java:49`) e não há rate limiting, throttling ou bloqueio de conta em nenhum ponto do projeto. Um atacante pode testar credenciais de forma ilimitada.

- **Evidência:** `SecurityConfig.java:49-51`; ausência de `RateLimiter`/`Bucket4j`/`throttle` em todo o projeto.
- **Correção:** filtro de rate limit (ex.: Bucket4j) nos endpoints de auth + bloqueio temporário após N falhas. Combinar com A09 (logar falhas de login).

**Ponto positivo do design:** o **preço é definido pelo servidor**, não pelo cliente — `AddPartToBudgetUseCase.java:31` busca `inventoryCatalogPort.getPartPrice()` no catálogo em vez de confiar no payload (`AddPartDTO` não aceita preço). ✔️

---

## 🔴 A07 — Identification & Authentication Failures · **Alta**

1. **Nenhuma política de senha.** `AddUserDto` e `RegisterAccountDto` têm `password` como `String` sem `@Size`/`@Pattern`; o use case apenas gera o hash. Aceita senha fraca/vazia (o exemplo do collection é `senha123`).
2. **Sem revogação de token** — não há logout, blacklist nem refresh token. Mitigado parcialmente pela expiração curta (1h, `JwtTokenService`, `JWT_EXPIRATION:3600`).
3. **Sem bloqueio de conta / MFA** (ver A04).

- **Evidência:** `AddUserDto.java`, `RegisterAccountDto.java` (sem Bean Validation); `JwtTokenService.java` (sem refresh/revogação).
- **Pontos positivos:** BCrypt com salt (`BCryptPasswordHasher`), `JWT_SECRET` via variável de ambiente obrigatória (`@Value("${JWT_SECRET}")`), sessão stateless.
- **Correção:** comprimento mínimo (≥12) + verificação de senha vazada; considerar refresh token + endpoint de logout com blacklist.

---

## 🟠 A01 — Broken Access Control · **Média**

`@EnableMethodSecurity` **está ativo** (`SecurityConfig.java:37`), portanto os `@PreAuthorize` são efetivos (não são no-op). Isso reduz a severidade do IDOR:

- Os endpoints `GET /customers/{id}`, `/vehicles/{id}`, `/service-orders/{id}` exigem `ROLE_USER`/`ROLE_ADMIN` (equipe da oficina). Um `ROLE_CUSTOMER` **não** os alcança — o acesso amplo entre a equipe é, em boa medida, esperado num sistema interno.
- **Gap real e confirmado:** `MetricsController` (`MetricsController.java:16`) **não tem `@PreAuthorize`** → cai em `anyRequest().authenticated()`, então **qualquer usuário autenticado, inclusive `ROLE_CUSTOMER`, lê métricas de qualquer OS por ID**. Baixa sensibilidade do dado, mas é quebra de autorização em nível de função (function-level access control).
- **Ausência de checagem de propriedade** (object-level): nenhum use case valida "este recurso pertence ao solicitante". Aceitável hoje pela separação de papéis, mas frágil — o primeiro endpoint por-id exposto a `CUSTOMER` vazaria dados de terceiros.

- **Evidência:** `MetricsController.java:14-28`; `GetCustomerByIdUseCase`, `GetVehicleByIdUseCase`, `GetServiceOrderByIdUseCase`, `OpenServiceOrderUseCase` (sem verificação de dono).
- **Correção:** anotar `MetricsController` com `@PreAuthorize`; ao expor recursos a clientes, comparar o `customerId` do JWT com o dono do recurso — padrão já usado corretamente em `/my-orders` (`ServiceOrderController.java:128`, `@PreAuthorize("hasRole('CUSTOMER')")` + claim `customerId`).

---

## 🟠 A02 — Cryptographic Failures · **Média**

- **Fallbacks fracos embutidos** no `docker-compose.yaml`: `JWT_SECRET:-chave-dev-minimo-256-bits-dev-...` (`:44`) e `DB_PASSWORD:-oficina_pass` (`:42`). Se a variável de ambiente não for definida, a aplicação sobe com um segredo conhecido.
- **Sem validação de robustez do `JWT_SECRET`** em runtime (`SecurityConfig.java:71`, `new SecretKeySpec(jwtSecret.getBytes(), "HmacSHA256")`).
- **CPF / e-mail / telefone em texto puro** no banco (`V1__create_initial_schema.sql`; `JPACustomerEntity`) — relevante para **LGPD**; considerar criptografia/tokenização de dados pessoais em produção.
- HS256 é aceitável; **RS256** (assimétrico) é preferível se houver múltiplos serviços validando o token.

- **Pontos positivos:** BCrypt (`BCryptPasswordEncoder`); `.env` no `.gitignore`; `.env.example` com `***REDACTED***`; nenhum segredo real commitado no histórico.
- **Correção:** remover os fallbacks de segredo do compose (falhar se ausente); validar tamanho mínimo do secret; avaliar criptografia em repouso do CPF.

---

## 🟠 A05 — Security Misconfiguration · **Média**

- **Swagger/OpenAPI público** (`SecurityConfig.java:50`) — `permitAll` em `/swagger-ui/**` e `/v3/api-docs/**` expõe toda a superfície da API (endpoints, DTOs, validações). Restringir ou desabilitar em produção.
- **Headers de segurança ausentes** — sem `X-Frame-Options`, `X-Content-Type-Options: nosniff`, HSTS (`Strict-Transport-Security`), CSP. Adicionar via `.headers(...)` no `SecurityFilterChain`.
- **Sem TLS/HTTPS forçado** na aplicação (normalmente terminado em proxy/LB, mas não há redirecionamento nem HSTS).

- **Pontos positivos:** actuator expõe **apenas** `/health` com `show-details=never` (`application.properties:19-20`); `spring.jpa.hibernate.ddl-auto=validate` (default); `show-sql=false`; Dockerfile roda como usuário **não-root** (`spring`, UID 1001); tratamento de erro genérico sem stack trace (`GlobalExceptionHandler`).

---

## 🟠 A06 — Vulnerable & Outdated Components · **Média**

- **Spring Boot 3.2.0 (nov/2023)** está desatualizado. A linha 3.2.x/3.3.x recebeu correções relevantes de CVE desde então — por exemplo, `UriComponentsBuilder` (CVE-2024-22243 / 22259 / 22262) e, em `spring-security-crypto`, CVE-2025-22228 no `BCryptPasswordEncoder` (que este projeto utiliza). Recomenda-se subir ao último patch suportado.
- **Sem SCA / verificação de dependências** — não há OWASP Dependency-Check nem Dependabot configurados em `.github/`.
- Gradle 8.5 — funcional, porém já há versões mais novas.

- **Correção:** atualizar Spring Boot; habilitar Dependabot + `dependency-check`; considerar `gradle --write-verification-metadata` para travar checksums.

---

## 🟠 A09 — Security Logging & Monitoring Failures · **Média**

- **Sem log de eventos de autenticação** (`LoginUseCase.java:23`) — login com sucesso/falha não é registrado → impossível detectar o brute-force descrito em A04.
- **Sem trilha de auditoria** para operações administrativas/CRUD sensíveis (criação/alteração/deleção de clientes, veículos, ordens).

- **Pontos positivos:** nenhum dado sensível (senha/CPF/token) aparece em logs; erros retornam mensagem genérica; Datadog disponível via overlay opcional (`docker-compose.datadog.yaml`, APM + logs); nível de log default `INFO`.
- **Correção:** logar tentativas de login (sem vazar credenciais) e ações administrativas; alertar sobre picos de falha de autenticação.

---

## 🟢 A03 — Injection · Conforme

Todos os repositórios usam **derived queries** do Spring Data ou `@Query` com **parâmetros nomeados** (`@Param`) — ex.: `JPABudgetItemRepository.java:17-28`, `JPACustomerRepository.java:13`. Nenhuma concatenação de SQL/JPQL nem native query montada manualmente. Validação de domínio robusta nos value objects:

- `CnpjCpf` — normalização + verificação de dígitos verificadores (CPF e CNPJ).
- `CarLicensePlate` — regex padrão Mercosul (`^[A-Z]{3}[0-9][A-Z][0-9]{2}$`).
- `Email` — validação por regex + normalização.
- `Phone` — validação de comprimento (10–11 dígitos).

- **Ressalva menor (defesa em profundidade):** os DTOs de request não usam `@Valid`/Bean Validation e campos como `name` em `Part`/`Service` não têm limite de tamanho (só `description`, 150 chars). A validação existe no domínio (retornando `DomainException` → 400), mas replicar parte na borda daria melhores mensagens de erro e proteção contra payloads gigantes.

---

## 🟢 A08 — Software & Data Integrity Failures · Conforme

Sem desserialização Java insegura (`ObjectInputStream`), sem Jackson polimórfico (`@JsonTypeInfo` / `enableDefaultTyping`), sem reflexão perigosa e sem upload de arquivos (`MultipartFile`). Gradle wrapper com `validateDistributionUrl=true`. As migrações Flyway (`V1`, `V2`) contêm apenas DDL/constraints — **sem seed de usuário/senha admin**.

- **Melhoria opcional:** assinatura de artefatos no CI e `verification-metadata.xml` para travar integridade das dependências.

---

## 🟢 A10 — Server-Side Request Forgery (SSRF) · Não aplicável

Nenhuma chamada HTTP de saída (`RestTemplate`, `WebClient`, `HttpClient`, `URL.openConnection`). O módulo `notifications` apenas registra eventos de domínio internamente (`ServiceOrderStatusChangedNotificationHandler`). Sem superfície de SSRF.

---

## Prioridades de correção

1. **Rate limiting + log de falhas de login** (A04 + A09) — maior risco real.
2. **Política de senha** nos DTOs de registro (A07).
3. **`@PreAuthorize` no `MetricsController`** e checagem de propriedade ao expor recursos a clientes (A01).
4. **Remover fallbacks de segredo** do `docker-compose` e **atualizar o Spring Boot** (A02 + A06).
5. **Headers de segurança** + restringir Swagger em produção (A05).

---

## Evidências-chave (arquivo:linha)

| Achado | Local |
|--------|-------|
| Login/registro sem rate limit | `config/SecurityConfig.java:49-51` |
| Method security habilitado (PreAuthorize efetivo) | `config/SecurityConfig.java:37` |
| `MetricsController` sem `@PreAuthorize` | `modules/serviceorder/presentation/controllers/MetricsController.java:16` |
| Autorização escopada correta (referência) | `modules/serviceorder/presentation/controllers/ServiceOrderController.java:128` |
| Senha sem validação | `modules/auth/presentation/dto/AddUserDto.java`, `RegisterAccountDto.java` |
| Fallback de `JWT_SECRET`/senha DB | `docker-compose.yaml:42,44` |
| JWT sem validação de tamanho do secret | `config/SecurityConfig.java:71` |
| CPF/e-mail/telefone em texto puro | `src/main/resources/db/migration/V1__create_initial_schema.sql` |
| Swagger público | `config/SecurityConfig.java:50` |
| Actuator restrito (positivo) | `src/main/resources/application.properties:19-20` |
| Query parametrizada (positivo) | `modules/serviceorder/infrastructure/persistence/repositories/JPABudgetItemRepository.java:17-28` |
| Preço definido pelo servidor (positivo) | `modules/serviceorder/application/use_cases/AddPartToBudgetUseCase.java:31` |
| BCrypt (positivo) | `modules/auth/infrastructure/security/BCryptPasswordHasher.java` |
| Container não-root (positivo) | `Dockerfile:23,25` |

---

*Relatório gerado por análise estática. Recomenda-se complementar com testes dinâmicos (DAST) e um SCA automatizado (OWASP Dependency-Check) no pipeline de CI.*
