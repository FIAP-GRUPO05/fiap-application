# Ambiente de desenvolvimento local (minikube)

Sobe, com um único `terraform apply`, um cluster Kubernetes local via minikube e
implanta nele a API e o PostgreSQL — o mesmo conteúdo dos manifestos em `k8s/`,
porém gerenciado pelo Terraform.

## Pré-requisitos

- Terraform >= 1.5
- minikube e kubectl no PATH
- Docker em execução (driver padrão)

## Uso

```bash
cd tf/dev
cp terraform.tfvars.example terraform.tfvars   # ajuste segredos e portas
terraform init
terraform apply
```

Ao final:

```bash
# URL acessível da API a partir do host
minikube -p oficina-dev service api-service -n oficina --url

# Swagger
#   <url>/swagger-ui.html

kubectl --context oficina-dev -n oficina get pods,svc,hpa
```

Para destruir tudo (inclusive o cluster):

```bash
terraform destroy
```

## O que é criado

| Recurso | Descrição |
|---|---|
| `minikube_cluster.dev` | Cluster local, profile `oficina-dev`, addons `default-storageclass`, `storage-provisioner` e `metrics-server` |
| `kubernetes_namespace.oficina` | Namespace `oficina` |
| `kubernetes_secret.postgres` | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| `kubernetes_persistent_volume_claim.postgres` | Volume de 2Gi para os dados do banco |
| `kubernetes_deployment.postgres` | `postgres:15-alpine` com probes `pg_isready` e estratégia `Recreate` |
| `kubernetes_service.postgres` | NodePort `30432` |
| `kubernetes_secret.api` | Datasource, `JWT_SECRET` e `JWT_EXPIRATION` |
| `kubernetes_deployment.api` | Imagem da API com probes em `/actuator/health` |
| `kubernetes_service.api` | NodePort `30080` |
| `kubernetes_horizontal_pod_autoscaler_v2.api` | 1 a 5 réplicas, alvo de 70% de CPU e 80% de memória |

## Diferenças em relação a `k8s/`

Os manifestos em `k8s/` continuam válidos para aplicação manual com `kubectl`.
Esta stack adiciona:

- **namespace dedicado** (`oficina`) em vez de `default`;
- **volume persistente** para o Postgres — sem ele os dados se perdem a cada
  recriação do pod;
- **probes** de startup/readiness/liveness, que garantem que a API só entre em
  serviço depois das migrations do Flyway e que o `terraform apply` só termine
  com tudo no ar;
- **limites de recursos** para o banco e para a API;
- **ordem de subida**: a API depende do rollout do Postgres.

O `replicas` do deployment da API tem `ignore_changes`, para que o HPA controle a
escala sem conflito com o Terraform.

## Notas

- As credenciais do provider `kubernetes` vêm dos atributos do
  `minikube_cluster`, o que permite criar cluster e workloads num único apply.
- O HPA depende do addon `metrics-server`; ele leva ~1 minuto após o apply para
  reportar métricas (antes disso o HPA mostra `<unknown>` em TARGETS).
- Os defaults de senha e `jwt_secret` são valores de desenvolvimento, iguais aos
  de `k8s/`. Sobrescreva-os em `terraform.tfvars` (arquivo já ignorado no git).
- Para usar uma imagem local da API em vez da publicada no Docker Hub:

  ```bash
  minikube -p oficina-dev image load minha-api:dev
  terraform apply -var 'api_image=minha-api:dev'
  ```
