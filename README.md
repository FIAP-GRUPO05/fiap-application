# fiap-application

API de gestão da oficina mecânica: clientes, veículos, estoque, serviços e ordens de serviço. Desenvolvida em Java 21 e Spring Boot.

📚 **[Documentação principal — fiap-docs](https://github.com/FIAP-GRUPO05/fiap-docs)** · [Guia da aplicação](https://github.com/FIAP-GRUPO05/fiap-docs/blob/main/guides/application.md)

## Requisitos

- Docker com Compose v2 para executar localmente.
- JDK 21 para executar o Gradle fora do container; o wrapper está no repositório.

## Executar localmente

No PowerShell, na raiz deste repositório:

```powershell
Copy-Item .env.example .env
# Edite .env: defina DB_PASSWORD e JWT_SECRET (ao menos 32 caracteres).
docker compose up --build -d postgres mailhog backend
docker compose ps
```

API: http://localhost:8080 · Swagger: http://localhost:8080/swagger-ui/index.html · MailHog: http://localhost:8025

```powershell
.\gradlew.bat test
docker compose down
```

No Linux/macOS, use `cp .env.example .env` e `./gradlew test`. O Compose acima inicia os três serviços necessários ao uso local.

Configuração completa, autenticação, endpoints e fluxo de negócio estão no [guia central](https://github.com/FIAP-GRUPO05/fiap-docs/blob/main/guides/application.md).
Para o ambiente AWS, consulte o [estado da infraestrutura](https://github.com/FIAP-GRUPO05/fiap-docs/blob/main/operations/infrastructure-state.md).
