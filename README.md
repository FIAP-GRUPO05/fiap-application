# fiap-application

API de gestão da oficina mecânica: clientes, veículos, estoque, serviços e ordens de serviço. Desenvolvida em Java 21 e Spring Boot.

📚 **[Documentação principal — fiap-docs](https://github.com/FIAP-GRUPO05/fiap-docs)**

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

Arquitetura, integrações e decisões do projeto estão na [documentação principal](https://github.com/FIAP-GRUPO05/fiap-docs).

## Membros da equipe

<table>
  <tr>
    <td align="center" width="160">
      <a href="https://www.linkedin.com/in/wesslima/" title="Wesley Lima">
        <img src="https://media.licdn.com/dms/image/v2/D4D03AQG__sMSpl_SEg/profile-displayphoto-scale_400_400/B4DaEHvVltHsAg-/0/1791131212617?e=1794441600&amp;v=beta&amp;t=VPPE-aIk2CNR6rhd3yPIdsPjOuVyl7gdsCxP6AlAlpg" width="100" height="100" alt="Foto de Wesley Lima"><br>
        <sub><b>Wesley Lima</b></sub>
      </a><br><sub>Backend Engineer</sub>
    </td>
    <td align="center" width="160">
      <a href="https://www.linkedin.com/in/tim-morgenstern-4581911b1/" title="Tim Morgenstern">
        <img src="https://media.licdn.com/dms/image/v2/C4E03AQG57Du9tsCS5A/profile-displayphoto-shrink_400_400/profile-displayphoto-shrink_400_400/0/1643980434981?e=1794441600&amp;v=beta&amp;t=6fVFPP0UXkaUU6K77-XJ3fPtGR2YvE1KRoRn9ze-Vog" width="100" height="100" alt="Foto de Tim Morgenstern"><br>
        <sub><b>Tim Morgenstern</b></sub>
      </a><br><sub>Backend Engineer</sub>
    </td>
    <td align="center" width="160">
      <a href="https://www.linkedin.com/in/matheus-pitas-baptista/" title="Matheus Pitas Baptista">
        <img src="https://media.licdn.com/dms/image/v2/D4D03AQHFS4VJk5WteA/profile-displayphoto-scale_400_400/B4DZv_LwbmHYAg-/0/1769512832179?e=1794441600&amp;v=beta&amp;t=gaI3wzS0F5BnXd5z0V9sS90uFcyxOLiK0vgWPnzMOJg" width="100" height="100" alt="Foto de Matheus Pitas Baptista"><br>
        <sub><b>Matheus Pitas Baptista</b></sub>
      </a><br><sub>Backend Engineer</sub>
    </td>
  </tr>
</table>
