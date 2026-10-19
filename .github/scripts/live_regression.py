"""Functional HTTPS regression using only records created by this execution.

Credentials and JWTs stay in memory. No request bodies or response bodies are logged.
Production runs retain synthetic service-order history, identified by the run UUID.
"""
import json
import os
import re
import secrets
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

environment = sys.argv[1]
if environment not in {"develop", "homol", "prod"}:
    raise ValueError("Invalid environment")
configuration = json.loads(os.environ["FIAP_RUNTIME_CONFIG"])[environment]
base = configuration["api_url"].rstrip("/")
if not re.fullmatch(r"https://[a-z0-9]+\.execute-api\.sa-east-1\.amazonaws\.com", base):
    raise ValueError("Only the project API Gateway endpoints are accepted")
run = uuid.uuid4().hex
covered = set()
successful = set()
requests = 0
admin_token = None


def call(method, path, body=None, *, token="admin", expected=(200,), operation=None):
    global requests
    headers = {"Content-Type": "application/json", "X-Correlation-ID": f"regression-{run}"}
    authentication = admin_token if token == "admin" else token
    if authentication:
        headers["Authorization"] = "Bearer " + authentication
    request = urllib.request.Request(base + path, method=method, headers=headers,
        data=json.dumps(body).encode() if body is not None else None)
    time.sleep(0.18)
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            status, data = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, data = error.code, error.read()
    route = operation or path.split("?")[0]
    if status not in expected:
        raise RuntimeError(f"Regression failed: {method} {route}; HTTP {status}; expected {expected}")
    covered.add((method.lower(), route))
    if 200 <= status < 400:
        successful.add((method.lower(), route))
    requests += 1
    try:
        return json.loads(data)
    except (ValueError, UnicodeDecodeError):
        return data.decode("utf-8")


def cpf():
    digits = [secrets.randbelow(10) for _ in range(9)]
    for length in (9, 10):
        remainder = sum(digit * weight for digit, weight in zip(digits, range(length + 1, 1, -1))) % 11
        digits.append(0 if remainder < 2 else 11 - remainder)
    return "".join(map(str, digits))


result = subprocess.run(["aws", "secretsmanager", "get-secret-value", "--secret-id",
    f"fiap/{environment}/admin", "--output", "json"], capture_output=True, text=True, check=True)
administrator = json.loads(json.loads(result.stdout)["SecretString"])
discovery = call("GET", "/.well-known/openid-configuration", token=None)
assert discovery["issuer"] == base
key = call("GET", "/.well-known/jwks.json", token=None)["keys"][0]
assert {"d", "p", "q", "dp", "dq", "qi"}.isdisjoint(key)
call("POST", "/auth/login", {"email": administrator["email"], "password": "invalid"}, token=None, expected=(401,))
admin_token = call("POST", "/auth/login", administrator, token=None)["accessToken"]
assert call("GET", "/actuator/health", token=None)["status"] == "UP"
for path in ("/", "/swagger-ui.html", "/swagger-ui/index.html"):
    call("GET", path, token=None)
schema = call("GET", "/v3/api-docs", token=None)
call("GET", "/service-orders", token=None, expected=(401,))
call("POST", "/auth/register", {"email": f"staff-{run}@example.test", "password": secrets.token_urlsafe(24)}, expected=(201,))
document = cpf()
account = {"email": f"customer-{run}@example.test", "password": secrets.token_urlsafe(24),
    "name": f"CI {run}", "phone": "11999998888", "cnpjCpf": document}
customer = call("POST", "/auth/register-account", account, token=None, expected=(201,))
customer_id = customer["customerId"]
customer_token = call("POST", "/auth/customer", {"cpf": document}, token=None)["accessToken"]
call("POST", "/auth/register", {"email": f"blocked-{run}@example.test", "password": "never-created"},
    token=customer_token, expected=(403,))
call("GET", "/customers", token=customer_token, expected=(403,))
call("GET", "/service-orders/my-orders", token=customer_token)
other_document = cpf()
other = {"name": f"CI disposable {run}", "email": f"other-{run}@example.test", "phone": "11999998888", "cnpjCpf": other_document}
other_id = call("POST", "/customers/register", other, expected=(201,))["customerId"]
call("PUT", f"/customers/alter/{other_id}", other, operation="/customers/alter/{id}")
call("GET", "/customers")
call("GET", f"/customers/{customer_id}", operation="/customers/{id}")
call("GET", f"/customers/cnpj-cpf/{document}", operation="/customers/cnpj-cpf/{cnpjCpf}")
plate = "CI" + chr(65 + secrets.randbelow(26)) + str(secrets.randbelow(10)) + chr(65 + secrets.randbelow(26)) + f"{secrets.randbelow(100):02}"
vehicle = {"customerId": customer_id, "carLicensePlate": plate, "model": "CI", "manufacturer": "CI", "kilometers": 100, "year": 2024}
vehicle_id = call("POST", "/vehicles/register", vehicle, expected=(201,))["vehicleId"]
call("PUT", f"/vehicles/alter/{vehicle_id}", vehicle, operation="/vehicles/alter/{id}")
call("GET", f"/vehicles/{vehicle_id}", operation="/vehicles/{id}")
call("GET", f"/vehicles/customer/{customer_id}", operation="/vehicles/customer/{id}")
call("GET", f"/vehicles/plate/{plate}", operation="/vehicles/plate/{plate}")
part = {"name": f"CI part {run}", "description": "Synthetic regression", "quantity": 30, "price": 10}
part_id = call("POST", "/part", part, expected=(201,))["id"]
service = {"name": f"CI service {run}", "description": "Synthetic regression", "price": 20}
service_id = call("POST", "/service", service, expected=(201,))["id"]
for entity, identifier, payload in (("part", part_id, part), ("service", service_id, service)):
    call("GET", f"/{entity}")
    call("GET", f"/{entity}/{identifier}", operation=f"/{entity}/{{id}}")
    call("PUT", f"/{entity}/{identifier}", payload, operation=f"/{entity}/{{id}}")
for direction in ("increase", "decrease"):
    call("PATCH", f"/part/{part_id}/stock/{direction}", {"amount": 1}, operation=f"/part/{{id}}/stock/{direction}")


def order(with_budget=False):
    payload = {"customerId": customer_id, "vehicleId": vehicle_id, "problemDescription": f"CI regression {run}"}
    if with_budget:
        payload["items"] = [{"type": "SERVICE", "itemId": service_id, "description": "CI service", "quantity": 1}]
        return call("POST", "/service-orders/with-budget", payload, expected=(201,))["serviceOrder"]["serviceOrderId"]
    return call("POST", "/service-orders", payload, expected=(201,))["serviceOrderId"]


def step(identifier, suffix, method="PATCH", body=None, token="admin", expected=(200,)):
    return call(method, f"/service-orders/{identifier}{suffix}", body, token=token, expected=expected,
        operation="/service-orders/{serviceOrderId}" + suffix)


identifier = order()
for direction in ("increase", "decrease"):
    step(identifier, f"/priority/{direction}")
call("GET", "/service-orders/pullNext")
call("GET", "/service-orders")
step(identifier, "", "GET")
call("GET", f"/service-orders/status/{identifier}", operation="/service-orders/status/{serviceOrderId}")
call("GET", f"/service-orders/customer/{customer_id}", operation="/service-orders/customer/{customerId}")
step(identifier, "/start-diagnosis")
step(identifier, "/budget/parts", "POST", {"itemId": part_id, "description": "CI part", "quantity": 1}, expected=(201,))
step(identifier, "/budget/services", "POST", {"itemId": service_id, "description": "CI service", "quantity": 1}, expected=(201,))
step(identifier, "/budget/items", "POST", {"items": [{"type": "PART", "itemId": part_id, "description": "CI part", "quantity": 1}]}, expected=(201,))
step(identifier, "/finalize-diagnosis", body={"diagnosis": "Synthetic functional regression"})
budget = step(identifier, "/budget", "GET", token=None)
assert float(budget["totalAmount"]) == 40
step(identifier, "/budget/decision", "GET", token=None)
step(identifier, "/budget/approve", "POST", token=None)
step(identifier, "/execute")
service_item = next(item["budgetItemId"] for item in budget["items"] if item["type"] == "SERVICE")
call("PATCH", f"/service-orders/{identifier}/budget/items/{service_item}/complete",
    operation="/service-orders/{serviceOrderId}/budget/items/{budgetItemId}/complete")
step(identifier, "/finalize")
assert step(identifier, "/deliver")["status"] == "DELIVERED"
step(identifier, "/metrics/average-execution-time", "GET")
own = call("GET", "/service-orders/my-orders", token=customer_token)
assert any(item["serviceOrderId"] == identifier for item in own)
for via_email in (False, True):
    rejected = order(with_budget=True)
    if via_email:
        step(rejected, "/budget/finalize")
    step(rejected, "/finalize-diagnosis", body={"diagnosis": "Synthetic rejection flow"})
    if via_email:
        step(rejected, "/budget/reject", "POST", token=None)
    else:
        step(rejected, "/reject-budget", token=customer_token)
call("DELETE", f"/vehicles/{vehicle_id}", expected=(409,), operation="/vehicles/{id}")
vehicle["carLicensePlate"] = "CD" + plate[2:]
disposable_vehicle = call("POST", "/vehicles/register", vehicle, expected=(201,))["vehicleId"]
call("DELETE", f"/vehicles/{disposable_vehicle}", expected=(204,), operation="/vehicles/{id}")
call("DELETE", f"/customers/{other_id}", expected=(204,), operation="/customers/{id}")
call("POST", "/auth/customer", {"cpf": other_document}, token=None, expected=(401,))
for entity, entity_id in (("part", part_id), ("service", service_id)):
    call("DELETE", f"/{entity}/{entity_id}", expected=(204,), operation=f"/{entity}/{{id}}")
operations = {(method, path) for path, item in schema["paths"].items() for method in item
              if method in {"get", "post", "put", "patch", "delete"}}
missing = operations - successful
if missing:
    raise RuntimeError("Missing successful regression operations: " + ", ".join(f"{m.upper()} {p}" for m, p in sorted(missing)))
with tempfile.TemporaryDirectory() as directory:
    from pathlib import Path
    request, response = Path(directory) / "request.json", Path(directory) / "response.json"
    request.write_text(json.dumps({"environment": environment, "require_apm": True, "require_business": True}))
    for attempt in range(12):
        metadata = json.loads(subprocess.run(["aws", "lambda", "invoke", "--function-name", "fiap-observability-probe",
            "--cli-binary-format", "raw-in-base64-out", "--payload", f"file://{request}", str(response), "--output", "json"],
            capture_output=True, text=True, check=True).stdout)
        telemetry = json.loads(response.read_text())
        if metadata.get("FunctionError"):
            raise RuntimeError("Business telemetry verification failed; inspect the private probe logs")
        if telemetry.get("healthy"):
            break
        time.sleep(15)
    else:
        raise RuntimeError("Expected business events and correlated logs did not reach New Relic")
print(json.dumps({"environment": environment, "run": run, "requests": requests,
    "successful_openapi_operations": len(operations), "missing_operations": 0, "status": "passed"}))
