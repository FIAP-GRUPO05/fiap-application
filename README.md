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

## Membros da equipe

<table>
  <tr>
    <td align="center" width="160">
      <a href="https://www.linkedin.com/in/wesslima/" title="Wesley Lima">
        <img src="https://raw.githubusercontent.com/FIAP-GRUPO05/fiap-docs/main/assets/team/wesley-lima.jpg" width="100" height="100" alt="Foto de Wesley Lima"><br>
        <sub><b>Wesley Lima</b></sub>
      </a><br><sub>Backend Engineer</sub>
    </td>
    <td align="center" width="160">
      <a href="https://www.linkedin.com/in/tim-morgenstern-4581911b1/" title="Tim Morgenstern">
        <img src="https://raw.githubusercontent.com/FIAP-GRUPO05/fiap-docs/main/assets/team/tim-morgenstern.jpg" width="100" height="100" alt="Foto de Tim Morgenstern"><br>
        <sub><b>Tim Morgenstern</b></sub>
      </a><br><sub>Backend Engineer</sub>
    </td>
    <td align="center" width="160">
      <a href="https://www.linkedin.com/in/matheus-pitas-baptista/" title="Matheus Pitas Baptista">
        <img src="https://raw.githubusercontent.com/FIAP-GRUPO05/fiap-docs/main/assets/team/matheus-pitas.jpg" width="100" height="100" alt="Foto de Matheus Pitas Baptista"><br>
        <sub><b>Matheus Pitas Baptista</b></sub>
      </a><br><sub>Backend Engineer</sub>
    </td>
  </tr>
</table>
