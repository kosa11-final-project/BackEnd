# Local monitoring

## 1. Start the backend

Run the Spring Boot application on port `8080`, then verify:

```text
http://localhost:8080/actuator/health
http://localhost:8080/actuator/prometheus
```

## 2. Start Prometheus and Grafana

Docker Desktop must be running.

```bash
docker compose -f compose.monitoring.yml up -d
```

Open the services:

- Prometheus: http://localhost:9090/targets
- Grafana: http://localhost:3000
- Grafana dashboard: `Dashboards > StockIt > StockIt Backend Overview`

The default local Grafana credentials are `admin` / `stockit_local`. Override
`GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` before using this outside a
local development environment.

## 3. Stop the monitoring stack

```bash
docker compose -f compose.monitoring.yml down
```

Add `-v` only when the locally collected metrics and Grafana state should also
be deleted.
