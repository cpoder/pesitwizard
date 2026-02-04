# PeSIT Wizard - Roadmap to Production

## Current State: 92% (Advanced Beta)

Last updated: 2026-02-01

---

## Phase 1: TLS/SSL Validation (Priority: HIGH)

**Objective**: Secure communications with Connect:Express

| ID | Task | Effort | Status |
|----|------|--------|--------|
| 1.1 | Configure SSL parameter tables (SSLPARM1/SSLPARM2) in CX | 2h | ✅ |
| 1.2 | Create test certificates (CA, server, client) compatible with PW/CX | 1h | ✅ |
| 1.3 | Modify `cx-setup-partner` to support SSLPARM | 1h | ✅ |
| 1.4 | Configure TLS keystores in PW Server via API | 2h | ✅ |
| 1.5 | Test unidirectional TLS PW Client -> CX | 2h | In Progress |
| 1.6 | Test unidirectional TLS CX -> PW Server | 2h | Pending |
| 1.7 | Test mutual TLS (mTLS) bidirectional | 2h | Pending |
| 1.8 | Add TLS tests to Docker integration suite | 2h | ✅ |

**Success Criteria**: Docker TLS tests pass, documentation complete

> **Progress (2026-02-01):**
> - PW Server TLS is working (upload keystore/truststore via API, TLS handshake validated)
> - PW Client TLS configured (upload truststore via API, TLSv1.2 protocol)
> - CX SSL listener starts (tom_apm -s SSLPARM1 -p 05001) but does not respond to handshakes
> - Investigation in progress: CX listener accepts TCP connections but closes immediately
>
> **To investigate:**
> - CX certificate format (certmgr.sh imports but tom_apm may not load them)
> - SSL logs in CX (no errors visible in LOG)
> - Test with native CX certificates instead of OpenSSL-generated ones

---

## Phase 2: Performance Testing (Priority: HIGH)

**Objective**: Establish limits and ensure stability under load

| ID | Task | Effort | Status |
|----|------|--------|--------|
| 2.1 | Create benchmark script with JMeter or Gatling | 4h | ✅ |
| 2.2 | Test: 10 concurrent transfers of 100MB | 2h | ✅ |
| 2.3 | Test: 100 concurrent transfers of 1MB | 2h | ✅ |
| 2.4 | Test: 1 transfer of 10GB (large file) | 2h | Pending |
| 2.5 | Test: transfers over 24h (long-term stability) | 1h setup | Pending |
| 2.6 | Measure: latency, throughput, CPU, memory | 2h | ✅ |
| 2.7 | Identify and fix bottlenecks | Variable | Pending |
| 2.8 | Document benchmarks and recommended limits | 2h | ✅ |

**Success Criteria**: Benchmarks documented, no memory leak over 24h

---

## Phase 3: Resilience Testing (Priority: HIGH)

**Objective**: Ensure robustness in the face of failures

| ID | Task | Effort | Status |
|----|------|--------|--------|
| 3.1 | Test: kill -9 during transfer, verify restart | 2h | Pending |
| 3.2 | Test: network cut (iptables drop) during transfer | 2h | Pending |
| 3.3 | Test: remote server timeout (slow connection) | 1h | ✅ |
| 3.4 | Test: disk full on receiving side | 1h | Pending |
| 3.5 | Test: expired certificate (TLS) | 1h | Pending |
| 3.6 | Test: rollback after partial failure | 2h | ✅ |
| 3.7 | Implement automatic retry with exponential backoff | 4h | Pending |
| 3.8 | Add circuit breaker for failing servers | 4h | Pending |

**Success Criteria**: All failure scenarios handled gracefully

> Note: Automated resilience tests created (run-resilience-tests.sh, run-restart-tests.sh)

---

## Phase 4: Security (Priority: HIGH)

**Objective**: Security audit and hardening

| ID | Task | Effort | Status |
|----|------|--------|--------|
| 4.1 | OWASP audit: injection, XSS, CSRF on REST API | 4h | Pending |
| 4.2 | Verify encryption of secrets (passwords, keystores) | 2h | Pending |
| 4.3 | Implement rate limiting on REST API | 2h | Pending |
| 4.4 | Add strict input validation (filenames, paths) | 2h | ✅ |
| 4.5 | Dependency scan (OWASP Dependency Check) | 1h | Pending |
| 4.6 | Configure Content Security Policy for the UI | 1h | Pending |
| 4.7 | Document the security policy | 2h | Pending |

**Success Criteria**: No critical/high vulnerabilities

---

## Phase 5: High Availability (Priority: MEDIUM)

**Objective**: Clustering and failover in production

| ID | Task | Effort | Status |
|----|------|--------|--------|
| 5.1 | Test 2-node cluster with load balancer | 4h | Pending |
| 5.2 | Test failover: kill primary node during transfer | 2h | Pending |
| 5.3 | Test transfer resumption after failover | 2h | Pending |
| 5.4 | Validate DB consistency with clustered PostgreSQL | 2h | Pending |
| 5.5 | Document recommended HA architecture | 2h | Pending |

**Success Criteria**: Transparent failover, no data loss

---

## Phase 6: Production Observability (Priority: MEDIUM)

**Objective**: Monitoring and alerting for operations

| ID | Task | Effort | Status |
|----|------|--------|--------|
| 6.1 | Grafana dashboard: transfers, errors, latency | 4h | ✅ |
| 6.2 | Alerts: failed transfer, full queue, certificate expiry | 2h | ✅ |
| 6.3 | Business metrics: daily volume, success rate, partners | 2h | Pending |
| 6.4 | Integration with alerting system (PagerDuty, Slack) | 2h | Pending |
| 6.5 | Structured logs (JSON) for ELK/Splunk | 2h | Pending |
| 6.6 | Distributed tracing (Jaeger/Zipkin) for debugging | 4h | Pending |

**Success Criteria**: Full operational visibility

---

## Phase 7: Documentation (Priority: MEDIUM)

**Objective**: Complete existing documentation (pesitwizard-docs)

**Existing Documentation** (already done):
- Client Guide: installation, configuration, usage, demo video, screenshots
- Server Guide: installation, configuration, connectors, secrets, observability
- Security: full TLS/mTLS (550 lines), private CA, certificate workflows
- API Reference: authentication, client API, server API
- Deployment: Docker, Kubernetes, Helm

| ID | Task | Effort | Status |
|----|------|--------|--------|
| 7.1 | Troubleshooting guide: PeSIT errors, network diagnostics | 3h | ✅ |
| 7.2 | Operational runbook: backup, restore, maintenance | 3h | ✅ |
| 7.3 | Connect:Express guide: interoperability, configuration | 2h | ✅ |
| 7.4 | Performance guide: tuning, benchmarks, limits | 2h | ✅ |

**Success Criteria**: Ops can resolve common issues without escalation

---

## Phase 8: Compliance and Audit (Priority: LOW)

**Objective**: Traceability for banking audits

| ID | Task | Effort | Status |
|----|------|--------|--------|
| 8.1 | Immutable audit log (who, what, when) | 4h | Pending |
| 8.2 | Configurable log retention | 2h | Pending |
| 8.3 | Log export for external audit | 2h | Pending |
| 8.4 | Automated compliance report | 4h | Pending |

**Success Criteria**: Complete audit trail for regulators

---

## Summary by Priority

| Priority | Phases | Total Effort | Impact |
|----------|--------|--------------|--------|
| **HIGH** | 1, 2, 3, 4 | ~60h | Blocking for production |
| **MEDIUM** | 5, 6, 7 | ~38h | Required for operations |
| **LOW** | 8 | ~12h | Nice-to-have |

**Total estimated**: ~110h of work (3 weeks full-time)

> Note: Existing documentation (pesitwizard-docs) already covers ~80% of needs.
> Only troubleshooting guides, ops runbook, and CX interop remain to be done.

---

## Production Go/No-Go Checklist

- [ ] TLS validated with a real partner (Phase 1)
- [ ] Benchmarks documented and acceptable (Phase 2)
- [ ] Resilience tests pass (Phase 3)
- [ ] Security audit with no critical findings (Phase 4)
- [x] User documentation complete (pesitwizard-docs)
- [ ] Monitoring and alerting in place (Phase 6)
- [ ] Operational runbook validated (Phase 7)
- [ ] Test with real-world volume for 1 week (Phase 2)

---

## History

| Date | Version | Changes |
|------|---------|---------|
| 2026-02-01 | 1.3 | Fix CX SSL config (certmgr.sh, SSLPARM tables, short names) |
| 2026-02-01 | 1.2 | Add resilience and restart tests, benchmark scripts |
| 2026-01-31 | 1.1 | Add TLS infrastructure and performance scripts |
| 2026-01-31 | 1.0 | Initial creation after CX integration validation |
