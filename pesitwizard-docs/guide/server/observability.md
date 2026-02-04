# Observability

PeSIT Wizard integrates comprehensive observability features: metrics, tracing, and logging.

## Prometheus Metrics

### Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: pesitwizard
      environment: ${ENVIRONMENT:production}
```

### Endpoint

Metrics are exposed at: `http://localhost:8080/actuator/prometheus`

### Available Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `pesitwizard.fpdu.total` | Counter | Total number of FPDUs processed |
| `pesitwizard.transfers.total` | Counter | Total number of transfers |
| `pesitwizard.transfers.bytes.total` | Counter | Total volume transferred (bytes) |
| `pesitwizard.transfers.duration` | Histogram | Transfer duration |
| `pesitwizard.connections.active` | Gauge | Active PeSIT connections |
| `pesitwizard.partners.total` | Gauge | Number of configured partners |

### Kubernetes ServiceMonitor

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: pesitwizard
  labels:
    release: prometheus
spec:
  selector:
    matchLabels:
      app: pesitwizard-server
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
```

### Grafana Dashboard

Import dashboard ID `XXXXX` or use the `grafana-dashboard.json` file:

```json
{
  "title": "PeSIT Wizard",
  "panels": [
    {
      "title": "Transfers per minute",
      "targets": [
        {
          "expr": "rate(pesitwizard_transfers_total[5m])"
        }
      ]
    }
  ]
}
```

## OpenTelemetry Tracing

### Configuration

```yaml
management:
  tracing:
    enabled: true
    sampling:
      probability: 1.0  # 100% of traces (reduce in production)

otel:
  exporter:
    otlp:
      endpoint: http://jaeger-collector:4317
  service:
    name: pesitwizard-server
```

### Environment Variables

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317
OTEL_SERVICE_NAME=pesitwizard-server
OTEL_TRACES_SAMPLER=parentbased_traceidratio
OTEL_TRACES_SAMPLER_ARG=0.1  # 10% sampling
```

### Jaeger

Deploy Jaeger to visualize traces:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jaeger
spec:
  template:
    spec:
      containers:
        - name: jaeger
          image: jaegertracing/all-in-one:1.52
          ports:
            - containerPort: 16686  # UI
            - containerPort: 4317   # OTLP gRPC
            - containerPort: 4318   # OTLP HTTP
```

### Custom Spans

PeSIT transfers automatically generate spans:

```
pesit-transfer (root span)
├── pesit-connection
│   ├── pesit-handshake
│   └── pesit-auth
├── pesit-file-transfer
│   ├── pesit-read-chunk (repeated)
│   └── pesit-write-chunk (repeated)
└── pesit-disconnect
```

## Structured Logging

### Logback Configuration

```xml
<!-- logback-spring.xml -->
<configuration>
  <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <includeMdcKeyName>traceId</includeMdcKeyName>
      <includeMdcKeyName>spanId</includeMdcKeyName>
      <includeMdcKeyName>partnerId</includeMdcKeyName>
      <includeMdcKeyName>transferId</includeMdcKeyName>
    </encoder>
  </appender>

  <root level="INFO">
    <appender-ref ref="JSON"/>
  </root>
</configuration>
```

### JSON Format

```json
{
  "@timestamp": "2024-01-15T10:30:00.000Z",
  "level": "INFO",
  "logger": "c.p.server.PesitTransferHandler",
  "message": "Transfer completed",
  "traceId": "abc123",
  "spanId": "def456",
  "partnerId": "BANK01",
  "transferId": "tx-789",
  "bytesTransferred": 1048576,
  "durationMs": 1234
}
```

### Loki / ELK

Collect logs with Promtail/Loki or Filebeat/Elasticsearch:

```yaml
# Promtail config
scrape_configs:
  - job_name: pesitwizard
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        regex: pesitwizard.*
        action: keep
```

## Health Checks

### Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Overall status |
| `/actuator/health/liveness` | Liveness probe (Kubernetes) |
| `/actuator/health/readiness` | Readiness probe (Kubernetes) |

### Configuration

```yaml
management:
  endpoint:
    health:
      show-details: when_authorized
      probes:
        enabled: true
  health:
    db:
      enabled: true
    diskspace:
      enabled: true
      threshold: 100MB
```

### Kubernetes Probes

```yaml
spec:
  containers:
    - name: pesitwizard
      livenessProbe:
        httpGet:
          path: /actuator/health/liveness
          port: 8080
        initialDelaySeconds: 30
        periodSeconds: 10
      readinessProbe:
        httpGet:
          path: /actuator/health/readiness
          port: 8080
        initialDelaySeconds: 10
        periodSeconds: 5
```

## Alerting

### Prometheus Rules

```yaml
groups:
  - name: pesitwizard
    rules:
      - alert: PesitwizardDown
        expr: up{job="pesitwizard"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "PeSIT Wizard is down"

      - alert: HighTransferErrorRate
        expr: rate(pesitwizard_transfers_total{status="error"}[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High transfer error rate"

      - alert: SlowTransfers
        expr: histogram_quantile(0.95, rate(pesitwizard_transfers_duration_bucket[5m])) > 60
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Transfers are slow (p95 > 60s)"
```

## Full Stack

For comprehensive observability, deploy:

```bash
# Prometheus + Grafana
helm install prometheus prometheus-community/kube-prometheus-stack

# Jaeger
helm install jaeger jaegertracing/jaeger

# Loki
helm install loki grafana/loki-stack
```
