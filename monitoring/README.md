# StockIt monitoring and AI incident analysis

This stack collects infrastructure and application metrics with Prometheus,
collects Docker logs with Grafana Alloy and Loki, and routes Grafana alerts to a
small incident analyzer. The analyzer removes common secrets from logs, asks
Gemini for a diagnosis, and sends the alert, evidence, impact, and recommended
actions to Microsoft Teams. AI output is advisory and never executes commands.

## Components

- Application EC2 (`10.0.10.150`): Spring Boot, Node Exporter, Grafana Alloy
- Monitoring EC2 (`10.0.28.166`): Prometheus, Grafana, Loki, incident analyzer
- Grafana alert rules: CPU, memory, disk, and backend availability
- Microsoft Teams: firing/resolved notifications and AI diagnostic results

## 1. Monitoring EC2

Create the server-only environment file. Never commit this file.

```bash
sudo mkdir -p /opt/stockit/config
sudo nano /opt/stockit/config/incident-analyzer.env
```

Use `infra/env/incident-analyzer.env.example` as the template. The real Gemini
API key and Teams webhook belong only in `/opt/stockit/config/incident-analyzer.env`.

Start or update the monitoring stack from `/opt/stockit`:

```bash
INCIDENT_ANALYZER_ENV_FILE=/opt/stockit/config/incident-analyzer.env \
  docker compose -f compose.monitoring.yml up -d --build
```

Verify:

```bash
docker compose -f compose.monitoring.yml ps
curl -fsS http://localhost:3100/ready
docker exec stockit-incident-analyzer python -c \
  "import urllib.request; print(urllib.request.urlopen('http://localhost:8080/health').read().decode())"
```

Open the services:

- Prometheus: http://localhost:9090/targets
- Grafana: http://localhost:3000
- Grafana dashboard: `Dashboards > StockIt > StockIt Backend Overview`
- Grafana logs: `Explore > Loki`

The default local Grafana credentials are `admin` / `stockit_local`. Override
`GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` before using this outside a
local development environment.

## 2. Application EC2 log agent

Create `/opt/stockit/config/logging-agent.env` from
`infra/env/logging-agent.env.example`, then start Alloy:

```bash
sudo nano /opt/stockit/config/logging-agent.env

cd /opt/stockit
LOGGING_AGENT_ENV_FILE=/opt/stockit/config/logging-agent.env \
  docker compose -f infra/compose/compose.logging-agent.yml up -d
```

The production push URL is:

```text
http://10.0.28.166:3100/loki/api/v1/push
```

Alloy reads only Docker containers whose names start with `stockit-`. It adds
`container`, `host`, and `environment` labels before sending the logs to Loki.

## 3. Network rules

- Monitoring EC2 TCP `3100`: allow only the Application EC2 security group.
- Application EC2 TCP `9100`: allow only the Monitoring EC2 security group.
- Application HTTPS TCP `443`: allow monitoring traffic within the VPC.
- Monitoring EC2 outbound HTTPS `443`: required for Gemini and Teams.
- Do not expose the incident analyzer port `8080`; it is Compose-internal only.

## 4. Alert flow and fallback

1. Grafana detects a firing or resolved rule.
2. The internal webhook calls `incident-analyzer:8080`.
3. The analyzer reads the last five minutes of error logs and current metrics.
4. Credentials, tokens, webhook signatures, cookies, and email addresses are
   masked before data is sent to Gemini.
5. Teams receives the diagnosis and proposed operator actions.

Duplicate firing alerts are suppressed for 15 minutes. If Gemini is unavailable
or rate-limited, Teams still receives a base alert with manual inspection steps.

## 5. Stop the stacks

```bash
docker compose -f compose.monitoring.yml down
docker compose -f infra/compose/compose.logging-agent.yml down
```

Add `-v` only when the locally collected metrics and Grafana state should also
be deleted.
