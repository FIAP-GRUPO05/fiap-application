#!/bin/bash
#
# Teste de carga com o fluxo real de negócio, em vez de martelar um endpoint só:
#   login -> cadastra cliente -> cadastra veículo -> abre N ordens de serviço
# e cada OS avança pelo fluxo completo, com uma espera aleatória entre as etapas:
#   RECEIVED -> IN_DIAGNOSIS -> AWAITING_APPROVAL -> IN_EXECUTION -> FINALIZED -> DELIVERED
# Uma parte das OSs tem o orçamento recusado (CANCELED). As esperas alimentam o tempo
# médio por status no New Relic.
#
# Uso: ./stress-flow.sh --user <email> --pass <senha> [--workers N] [--orders N]
#                       [--delay MIN-MAX] [--reject PCT] [url]
#
# Sem url, o alvo é o API Gateway (fiap-api-gateway) descoberto via aws CLI: o login
# passa pela lambda-token e o resto pelo authorizer JWT, como um cliente real.
# Se o gateway não for encontrado, cai no Load Balancer do Service via kubectl.
#
# ATENÇÃO: grava dados de verdade no banco do ambiente apontado.

set -uo pipefail

NAMESPACE=${NAMESPACE:-prod}
SERVICE=${SERVICE:-api-service}
PORT=${PORT:-8080}
GATEWAY_NAME=${GATEWAY_NAME:-fiap-api-gateway}
WORKERS=${WORKERS:-4}    # fluxos completos rodando em paralelo
ORDERS=${ORDERS:-5}      # ordens de serviço abertas por cliente criado
DELAY=${DELAY:-5-20}     # espera, em segundos, entre uma etapa da OS e a próxima
REJECT=${REJECT:-10}     # % de OSs com o orçamento recusado
LOGIN_USER=${LOGIN_USER:-}
LOGIN_PASS=${LOGIN_PASS:-}
BASE=${API_URL:-}

uso() {
  echo "Uso: $0 --user <email> --pass <senha> [--workers N] [--orders N] [--delay MIN-MAX] [--reject PCT] [url]"
  echo "  --workers  fluxos paralelos (padrão: 4)"
  echo "  --orders   ordens de serviço por cliente (padrão: 5)"
  echo "  --delay    segundos entre as etapas de cada OS (padrão: 5-20)"
  echo "  --reject   % de OSs com orçamento recusado (padrão: 10)"
  echo "  url        opcional; sem ela usa o API Gateway ${GATEWAY_NAME} (aws CLI)"
  echo "             e, se não achar, o Service ${SERVICE} via kubectl"
}

while [[ "${1:-}" == --* ]]; do
  case "$1" in
    --user)    shift; LOGIN_USER=${1:-} ;;
    --pass)    shift; LOGIN_PASS=${1:-} ;;
    --workers) shift; WORKERS=${1:-4} ;;
    --orders)  shift; ORDERS=${1:-5} ;;
    --delay)   shift; DELAY=${1:-5-20} ;;
    --reject)  shift; REJECT=${1:-10} ;;
    -h|--help) uso; exit 0 ;;
    *) echo "Opção desconhecida: $1"; uso; exit 1 ;;
  esac
  shift
done
[[ "${1:-}" == http://* || "${1:-}" == https://* ]] && BASE=$1

if [ -z "$LOGIN_USER" ] || [ -z "$LOGIN_PASS" ]; then
  echo "Credenciais são obrigatórias: o fluxo inteiro exige um token de USER/ADMIN."
  uso
  exit 1
fi

if [[ ! "$DELAY" =~ ^[0-9]+-[0-9]+$ ]] || [ "${DELAY%-*}" -gt "${DELAY#*-}" ]; then
  echo "--delay deve ser MIN-MAX em segundos, com MIN <= MAX (ex.: 5-20)."
  exit 1
fi
DELAY_MIN=${DELAY%-*}
DELAY_MAX=${DELAY#*-}

if [ -z "$BASE" ]; then
  BASE=$(aws apigatewayv2 get-apis \
    --query "Items[?Name=='${GATEWAY_NAME}'].ApiEndpoint | [0]" --output text 2>/dev/null)
  [ "$BASE" == "None" ] && BASE=""
fi

if [ -z "$BASE" ]; then
  echo "API Gateway ${GATEWAY_NAME} não encontrado; usando o Load Balancer do Service."
  HOST=$(kubectl get svc "$SERVICE" -n "$NAMESPACE" \
    -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null)
  [ -z "$HOST" ] && HOST=$(kubectl get svc "$SERVICE" -n "$NAMESPACE" \
    -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null)
  if [ -z "$HOST" ]; then
    echo "Não foi possível descobrir a URL pelo cluster; informe-a como argumento."
    exit 1
  fi
  BASE="http://${HOST}:${PORT}"
fi

STATS=$(mktemp)
PIDS=()

resumo() {
  echo ""
  echo "===================== RESUMO ====================="
  printf "%-13s %-6s %s\n" "ETAPA" "HTTP" "QTD"
  awk '{c[$1" "$2]++} END {for (k in c) {split(k,p," "); printf "%-13s %-6s %s\n", p[1], p[2], c[k]}}' \
    "$STATS" | sort
  echo "=================================================="
}

# Os workers disparam um subprocesso por OS: encerra a árvore inteira
mata_arvore() {
  local filho
  for filho in $(pgrep -P "$1" 2>/dev/null); do mata_arvore "$filho"; done
  kill "$1" 2>/dev/null
}

encerra() {
  trap - EXIT INT TERM
  for pid in "${PIDS[@]:-}"; do [ -n "$pid" ] && mata_arvore "$pid"; done
  wait 2>/dev/null
  resumo
  rm -f "$STATS"
  exit 0
}
trap encerra EXIT INT TERM

# CPF com dígitos verificadores válidos — o cadastro rejeita documento inválido
gera_cpf() {
  local n=() soma=0 r i
  for i in {0..8}; do n[i]=$((RANDOM % 10)); done
  for i in {0..8}; do soma=$((soma + n[i] * (10 - i))); done
  r=$((soma % 11)); if [ $r -lt 2 ]; then n[9]=0; else n[9]=$((11 - r)); fi
  soma=0
  for i in {0..9}; do soma=$((soma + n[i] * (11 - i))); done
  r=$((soma % 11)); if [ $r -lt 2 ]; then n[10]=0; else n[10]=$((11 - r)); fi
  printf "%s" "${n[*]}" | tr -d ' '
}

gera_placa() {
  local l=({A..Z}) d
  d=$((RANDOM % 10))
  printf "%s%s%s%d%s%d%d" "${l[RANDOM%26]}" "${l[RANDOM%26]}" "${l[RANDOM%26]}" \
    "$d" "${l[RANDOM%26]}" "$((RANDOM%10))" "$((RANDOM%10))"
}

# req <etapa> <método> <path> <token> [body] -> ecoa o corpo da resposta
req() {
  local etapa=$1 metodo=$2 path=$3 token=$4 body=${5:-}
  local args=(-s -m 30 -w $'\n%{http_code}' -X "$metodo" -H "Content-Type: application/json")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-d "$body")

  local saida code
  saida=$(curl "${args[@]}" "${BASE}${path}" 2>/dev/null)
  code=${saida##*$'\n'}
  echo "$etapa ${code:-erro}" >> "$STATS"
  printf '%s' "${saida%$'\n'*}"
}

espera() {
  sleep $((DELAY_MIN + RANDOM % (DELAY_MAX - DELAY_MIN + 1)))
}

# Leva uma OS de RECEIVED até DELIVERED (ou CANCELED), uma etapa por vez.
# Se uma etapa falhar, a OS fica parada no status em que estava.
ciclo_os() {
  local token=$1 os=$2 item
  local p="/service-orders/${os}"

  espera
  req diagnostico PATCH "$p/start-diagnosis" "$token" > /dev/null

  # O orçamento precisa ter itens antes de fechar o diagnóstico (o finalize-diagnosis
  # ignora os itens do corpo); fechar o diagnóstico finaliza o orçamento -> AWAITING_APPROVAL
  espera
  req orcamento POST "$p/budget/items" "$token" \
    "{\"items\":[{\"type\":\"SERVICE\",\"itemId\":\"${SERVICE_ID}\",\"description\":\"Revisão (carga)\",\"quantity\":1}]}" \
    > /dev/null
  req diagnostico_fim PATCH "$p/finalize-diagnosis" "$token" \
    '{"diagnosis":"Desgaste identificado (carga)"}' > /dev/null

  espera
  if [ $((RANDOM % 100)) -lt "$REJECT" ]; then
    req recusa PATCH "$p/reject-budget" "$token" > /dev/null
    return
  fi
  req aprovacao POST "$p/budget/approve" "" > /dev/null
  req execucao PATCH "$p/execute" "$token" > /dev/null

  # A OS só finaliza com todos os itens de serviço concluídos
  espera
  for item in $(req orcamento_get GET "$p/budget" "$token" \
      | jq -r '.items[]? | select(.type == "SERVICE") | .budgetItemId'); do
    req item PATCH "$p/budget/items/${item}/complete" "$token" > /dev/null
  done
  req finalizacao PATCH "$p/finalize" "$token" > /dev/null

  espera
  req entrega PATCH "$p/deliver" "$token" > /dev/null
}

worker() {
  local token cid vid cpf email placa i os
  while true; do
    token=$(req login POST /auth/login "" \
      "{\"email\":\"${LOGIN_USER}\",\"password\":\"${LOGIN_PASS}\"}" | jq -r '.accessToken // empty')
    if [ -z "$token" ]; then sleep 2; continue; fi

    cpf=$(gera_cpf)
    email="x-timbeck1997@hotmail.com"
    cid=$(req cliente POST /customers/register "$token" \
      "{\"name\":\"Cliente Carga ${RANDOM}\",\"email\":\"${email}\",\"phone\":\"11999999999\",\"cnpjCpf\":\"${cpf}\"}" \
      | jq -r '.customerId // empty')
    if [ -z "$cid" ]; then continue; fi

    placa=$(gera_placa)
    vid=$(req veiculo POST /vehicles/register "$token" \
      "{\"customerId\":\"${cid}\",\"carLicensePlate\":\"${placa}\",\"model\":\"Modelo ${RANDOM}\",\"manufacturer\":\"Fabricante\",\"kilometers\":$((RANDOM % 200000)),\"year\":$((2005 + RANDOM % 20))}" \
      | jq -r '.vehicleId // empty')
    if [ -z "$vid" ]; then continue; fi

    for ((i = 1; i <= ORDERS; i++)); do
      os=$(req ordem POST /service-orders "$token" \
        "{\"customerId\":\"${cid}\",\"vehicleId\":\"${vid}\",\"problemDescription\":\"Barulho no motor (carga ${i})\"}" \
        | jq -r '.serviceOrderId // empty')
      [ -n "$os" ] && ciclo_os "$token" "$os" &
    done
    # Espera as OSs deste cliente terminarem: limita o paralelismo a WORKERS x ORDERS
    wait
  done
}

# Item de catálogo usado no orçamento de todas as OSs
SETUP_TOKEN=$(req login POST /auth/login "" \
  "{\"email\":\"${LOGIN_USER}\",\"password\":\"${LOGIN_PASS}\"}" | jq -r '.accessToken // empty')
SERVICE_ID=$(req servico POST /service "$SETUP_TOKEN" \
  '{"name":"Revisão (carga)","description":"Serviço usado pelo teste de carga","price":150.00}' \
  | jq -r '.id // empty')
if [ -z "$SERVICE_ID" ]; then
  echo "Não foi possível cadastrar o serviço de catálogo usado nos orçamentos (login ou POST /service falhou)."
  exit 1
fi

echo "Alvo: ${BASE}"
echo "Fluxo: login -> cliente -> veículo -> ${ORDERS} ordens de serviço"
echo "Cada OS: diagnóstico -> orçamento -> aprovação (${REJECT}% recusa) -> execução -> finalização -> entrega"
echo "Espera entre etapas: ${DELAY_MIN}-${DELAY_MAX}s"
echo "Workers em paralelo: ${WORKERS}"
echo "Ctrl+C encerra e mostra o resumo."
echo "--------------------------------------------------"

for ((w = 1; w <= WORKERS; w++)); do
  worker &
  PIDS+=($!)
done

while true; do
  sleep 15
  echo "--- parcial ($(wc -l < "$STATS") requisições) ---"
  awk '{c[$1" "$2]++} END {for (k in c) {split(k,p," "); printf "  %-13s %-6s %s\n", p[1], p[2], c[k]}}' \
    "$STATS" | sort
done
