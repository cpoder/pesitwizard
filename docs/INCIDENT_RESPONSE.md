# PeSIT Wizard Incident Response Runbook

This document provides procedures for responding to incidents affecting PeSIT Wizard in production.

## Table of Contents

- [Incident Classification](#incident-classification)
- [Alert to Action Mapping](#alert-to-action-mapping)
- [Escalation Matrix](#escalation-matrix)
- [Communication Templates](#communication-templates)
- [Recovery Procedures](#recovery-procedures)
- [Post-Incident Review](#post-incident-review)

---

## Incident Classification

### Severity Levels

| Severity | Definition | Response Time | Examples |
|----------|------------|---------------|----------|
| **SEV1 - Critical** | Complete service outage or data loss risk | 15 minutes | All transfers failing, cluster down, data corruption |
| **SEV2 - High** | Major feature unavailable or degraded | 30 minutes | High failure rate (>10%), single node down, slow transfers |
| **SEV3 - Medium** | Minor feature issue, workaround exists | 2 hours | Single partner affected, non-critical API down |
| **SEV4 - Low** | Cosmetic or minor issue | 24 hours | UI glitches, non-critical alerts |

### Impact Assessment Matrix

| Users Affected | Data Impact | Service Impact | Severity |
|----------------|-------------|----------------|----------|
| All | Loss risk | Unavailable | SEV1 |
| All | None | Degraded (>50%) | SEV1 |
| Many (>10%) | None | Degraded | SEV2 |
| Few (<10%) | None | Degraded | SEV3 |
| None | None | Minor | SEV4 |

---

## Alert to Action Mapping

### Critical Alerts (SEV1)

#### PesitWizardDown
**Alert**: `up{job=~".*pesit.*"} == 0`

**Symptoms**:
- Health endpoint not responding
- No metrics being scraped
- Partners reporting connection failures

**Immediate Actions**:
1. Check pod status: `kubectl get pods -n pesitwizard`
2. Check pod logs: `kubectl logs -l app=pesitwizard-server -n pesitwizard --tail=100`
3. Check node health: `kubectl get nodes`
4. Check recent deployments: `helm history pesitwizard -n pesitwizard`

**Recovery**:
- If pods are CrashLoopBackOff: See [Application Crash Recovery](#application-crash-recovery)
- If nodes are NotReady: See [Node Failure Recovery](#node-failure-recovery)
- If recent deployment: See [Rollback Procedure](#rollback-procedure)

---

#### CriticalTransferFailureRate
**Alert**: Transfer failure rate > 25% for 2 minutes

**Symptoms**:
- Multiple partners reporting failures
- Error rate spike in dashboards
- Increased protocol errors

**Immediate Actions**:
1. Check error distribution:
   ```bash
   kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
     curl -s localhost:8080/actuator/metrics/pesitwizard.error.total | jq .
   ```
2. Check for common error types in logs
3. Verify database connectivity
4. Check partner server status

**Recovery**:
- If database issue: See [Database Recovery](#database-recovery)
- If network issue: See [Network Recovery](#network-recovery)
- If partner issue: Contact partner operations team

---

#### NoActiveConnections
**Alert**: `pesitwizard_connections_active == 0 and pesitwizard_transfers_active > 0`

**Symptoms**:
- Active transfers with no connections
- Possible connection leak
- Memory increasing

**Immediate Actions**:
1. Check connection pool metrics:
   ```bash
   kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
     curl -s localhost:8080/actuator/metrics/hikaricp.connections | jq .
   ```
2. Check thread dumps for blocked threads
3. Review recent code changes

**Recovery**:
1. Rolling restart: `kubectl rollout restart deployment/pesitwizard-server -n pesitwizard`
2. If persists: [Application Crash Recovery](#application-crash-recovery)

---

### High Severity Alerts (SEV2)

#### HighTransferFailureRate
**Alert**: Transfer failure rate > 10% for 5 minutes

**Symptoms**:
- Increased error rate
- Some transfers completing
- Partner complaints

**Immediate Actions**:
1. Identify affected partners:
   ```bash
   kubectl logs -l app=pesitwizard-server -n pesitwizard | grep -i "transfer failed" | head -20
   ```
2. Check for patterns (file size, partner, time)
3. Review protocol errors

**Recovery**:
- Identify root cause before taking action
- If specific partner: Check partner configuration
- If specific file type: Check file handling code
- If intermittent: May be network instability

---

#### HighActiveTransfers
**Alert**: `pesitwizard_transfers_active > 180` for 10 minutes

**Symptoms**:
- Approaching capacity limit (200)
- Increased latency
- Queue buildup

**Immediate Actions**:
1. Check if this is expected load
2. Monitor for continued growth
3. Prepare to scale if needed

**Recovery**:
1. If expected: Scale up pods
   ```bash
   kubectl scale deployment/pesitwizard-server --replicas=5 -n pesitwizard
   ```
2. If unexpected: Investigate source of load
3. Consider rate limiting if abuse

---

#### TLSCertificateExpiringCritical
**Alert**: Certificate expires in < 7 days

**Symptoms**:
- Certificate expiration warning
- Risk of service disruption

**Immediate Actions**:
1. Verify certificate expiry date
2. Check cert-manager status (if used)
3. Prepare new certificate

**Recovery**:
1. Renew certificate immediately
2. Update Kubernetes secrets
3. Rolling restart to pick up new cert

---

### Medium Severity Alerts (SEV3)

#### HighConnectionFailures
**Alert**: > 5 connection failures/second for 5 minutes

**Symptoms**:
- Intermittent connection issues
- Partner connectivity problems

**Actions**:
1. Check network connectivity to partners
2. Verify firewall rules
3. Check TLS configuration

---

#### SlowTransfers
**Alert**: P95 transfer duration > 5 minutes for 10 minutes

**Symptoms**:
- Transfers taking longer than usual
- No failures, just slow

**Actions**:
1. Check database performance
2. Check disk I/O
3. Check network bandwidth
4. Review file sizes being transferred

---

#### HighJVMMemoryUsage
**Alert**: JVM heap > 85% for 10 minutes

**Symptoms**:
- Memory pressure
- Potential OOM risk

**Actions**:
1. Check for memory leaks
2. Review GC logs
3. Consider heap dump if persistent
4. May need to increase memory limits

---

## Escalation Matrix

### Escalation Tiers

| Tier | Role | Response Time | Capabilities |
|------|------|---------------|--------------|
| L1 | On-call Engineer | 15 min | Basic troubleshooting, restarts |
| L2 | Senior Engineer | 30 min | Code-level debugging, config changes |
| L3 | Platform Lead | 1 hour | Architecture decisions, major changes |
| L4 | Engineering Director | 2 hours | Business decisions, external comms |

### Escalation Triggers

**Escalate to L2 when**:
- Issue not resolved within 30 minutes
- Root cause unknown
- Code change may be required
- Multiple components affected

**Escalate to L3 when**:
- Issue not resolved within 1 hour
- Data loss possible
- Architecture change needed
- Multiple teams affected

**Escalate to L4 when**:
- Customer-facing impact > 2 hours
- Legal/compliance implications
- External communication needed
- Business continuity decisions required

### On-Call Rotation

| Week | Primary | Secondary | L2 Backup |
|------|---------|-----------|-----------|
| Current | Check PagerDuty | Check PagerDuty | Check PagerDuty |

**Contact Methods**:
1. PagerDuty (preferred)
2. Slack: #pesitwizard-oncall
3. Phone: See PagerDuty contact info

---

## Communication Templates

### Internal Status Update

```
[INCIDENT] PeSIT Wizard - {SEVERITY} - {TITLE}

Status: {Investigating | Identified | Monitoring | Resolved}
Impact: {Description of user/business impact}
Started: {Time} UTC
Duration: {Duration}

Current State:
- {What is happening}
- {What is affected}

Actions Taken:
- {Action 1}
- {Action 2}

Next Steps:
- {Next action with ETA}

Incident Commander: {Name}
Next Update: {Time} UTC
```

### Customer Communication (SEV1/SEV2)

```
Subject: [Service Alert] PeSIT Wizard - File Transfer Service Degradation

Dear Customer,

We are currently experiencing {brief description of issue} affecting the PeSIT Wizard file transfer service.

Impact: {What customers are experiencing}
Started: {Time} UTC
Status: {Current status}

Our team is actively working to resolve this issue. We will provide updates every {30 minutes / 1 hour}.

For urgent matters, please contact support@example.com.

We apologize for any inconvenience.

PeSIT Wizard Operations Team
```

### Partner Notification

```
Subject: [URGENT] PeSIT Wizard - Service Notification

Partner ID: {Partner ID}
Partner Name: {Partner Name}

We are notifying you of an ongoing service issue:

Issue: {Description}
Impact: {Impact on file transfers}
Workaround: {If available}
Expected Resolution: {ETA if known}

Please hold non-critical transfers until service is restored.

Contact: support@example.com
Status Page: https://status.pesitwizard.io
```

---

## Recovery Procedures

### Application Crash Recovery

**Symptoms**: Pods in CrashLoopBackOff or not starting

**Steps**:

1. **Gather Information**
   ```bash
   # Pod status
   kubectl get pods -n pesitwizard

   # Pod events
   kubectl describe pod pesitwizard-server-0 -n pesitwizard

   # Logs
   kubectl logs pesitwizard-server-0 -n pesitwizard --previous
   ```

2. **Check Common Causes**
   - OOMKilled: Increase memory limits
   - ConfigMap/Secret missing: Verify secrets exist
   - Database connection: Check DB status
   - Image pull error: Check registry access

3. **Recovery Actions**

   **If OOMKilled**:
   ```bash
   kubectl set resources deployment/pesitwizard-server \
     --limits=memory=6Gi -n pesitwizard
   ```

   **If configuration issue**:
   ```bash
   # Verify secrets
   kubectl get secrets -n pesitwizard

   # Check ConfigMap
   kubectl get configmap -n pesitwizard

   # Rollback if recent change
   helm rollback pesitwizard -n pesitwizard
   ```

   **If database issue**:
   See [Database Recovery](#database-recovery)

4. **Verify Recovery**
   ```bash
   kubectl wait --for=condition=ready pod -l app=pesitwizard-server \
     -n pesitwizard --timeout=300s

   curl -sf https://api.pesit.example.com/actuator/health
   ```

---

### Node Failure Recovery

**Symptoms**: Node in NotReady state, pods evicted

**Steps**:

1. **Assess Node Status**
   ```bash
   kubectl get nodes
   kubectl describe node <node-name>
   ```

2. **Check Node Health**
   - Cloud provider console for instance status
   - Check for kubelet issues
   - Verify network connectivity

3. **Recovery Actions**

   **If node recoverable**:
   ```bash
   # Cordon to prevent new pods
   kubectl cordon <node-name>

   # Restart kubelet (SSH to node)
   sudo systemctl restart kubelet

   # Uncordon when healthy
   kubectl uncordon <node-name>
   ```

   **If node not recoverable**:
   ```bash
   # Drain node (move pods)
   kubectl drain <node-name> --ignore-daemonsets --delete-emptydir-data

   # Remove from cluster
   kubectl delete node <node-name>

   # Provision replacement via autoscaler or manually
   ```

4. **Verify Pod Distribution**
   ```bash
   kubectl get pods -n pesitwizard -o wide
   ```

---

### Database Recovery

**Symptoms**: Connection errors, slow queries, replication lag

**Steps**:

1. **Check Database Status**
   ```bash
   # PostgreSQL status
   kubectl exec -it pesitwizard-db-0 -n pesitwizard -- pg_isready

   # Replication status
   kubectl exec -it pesitwizard-db-0 -n pesitwizard -- \
     psql -U postgres -c "SELECT * FROM pg_stat_replication;"
   ```

2. **Check Connection Pool**
   ```bash
   kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
     curl -s localhost:8080/actuator/metrics/hikaricp.connections | jq .
   ```

3. **Recovery Actions**

   **If connection exhaustion**:
   ```bash
   # Restart application to reset connections
   kubectl rollout restart deployment/pesitwizard-server -n pesitwizard
   ```

   **If primary database down**:
   ```bash
   # Trigger failover (if using managed DB)
   # AWS RDS: aws rds failover-db-cluster
   # Or manually promote replica
   kubectl exec -it pesitwizard-db-1 -n pesitwizard -- \
     pg_ctl promote -D /var/lib/postgresql/data
   ```

   **If data corruption**:
   ```bash
   # Restore from backup
   # 1. Stop application
   kubectl scale deployment/pesitwizard-server --replicas=0 -n pesitwizard

   # 2. Restore database
   pg_restore -h localhost -U pesitwizard -d pesitwizard backup.dump

   # 3. Restart application
   kubectl scale deployment/pesitwizard-server --replicas=3 -n pesitwizard
   ```

---

### Network Recovery

**Symptoms**: Connection timeouts, intermittent failures, DNS issues

**Steps**:

1. **Check Network Connectivity**
   ```bash
   # Pod to pod
   kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
     nc -zv pesitwizard-server-1.pesitwizard-server 8080

   # Pod to database
   kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
     nc -zv pesitwizard-db 5432

   # DNS resolution
   kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
     nslookup pesitwizard-db
   ```

2. **Check Network Policies**
   ```bash
   kubectl get networkpolicies -n pesitwizard
   kubectl describe networkpolicy -n pesitwizard
   ```

3. **Recovery Actions**

   **If DNS issue**:
   ```bash
   # Restart CoreDNS
   kubectl rollout restart deployment/coredns -n kube-system
   ```

   **If network policy blocking**:
   ```bash
   # Temporarily allow all (emergency only)
   kubectl delete networkpolicy --all -n pesitwizard

   # Then fix and reapply correct policies
   kubectl apply -f deployment/kubernetes/network-policies.yaml
   ```

   **If load balancer issue**:
   ```bash
   # Check service status
   kubectl describe svc pesitwizard-pesit-lb -n pesitwizard

   # Check cloud LB health
   # AWS: aws elbv2 describe-target-health
   ```

---

### Cluster Split-Brain Recovery

**Symptoms**: Multiple leaders, inconsistent state, cluster not forming

**Steps**:

1. **Identify Cluster State**
   ```bash
   # Check each pod's view of cluster
   for i in 0 1 2; do
     echo "=== Pod $i ==="
     kubectl exec -it pesitwizard-server-$i -n pesitwizard -- \
       curl -s localhost:8080/api/v1/cluster/members | jq .
   done
   ```

2. **Check Network Partition**
   ```bash
   # Test connectivity between pods
   kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
     ping -c 3 pesitwizard-server-1.pesitwizard-server
   ```

3. **Recovery Actions**

   **Controlled cluster restart**:
   ```bash
   # Scale to single node
   kubectl scale statefulset/pesitwizard-server --replicas=1 -n pesitwizard

   # Wait for single leader
   sleep 60

   # Verify single node healthy
   kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
     curl -s localhost:8080/actuator/health

   # Scale back up
   kubectl scale statefulset/pesitwizard-server --replicas=3 -n pesitwizard

   # Verify cluster formation
   kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
     curl -s localhost:8080/api/v1/cluster/members | jq .
   ```

---

### Rollback Procedure

**When to rollback**: Recent deployment correlated with incident

**Steps**:

1. **Verify Recent Deployment**
   ```bash
   helm history pesitwizard -n pesitwizard
   ```

2. **Perform Rollback**
   ```bash
   # Rollback to previous version
   helm rollback pesitwizard -n pesitwizard

   # Or to specific revision
   helm rollback pesitwizard 5 -n pesitwizard
   ```

3. **Verify Rollback**
   ```bash
   # Check deployment
   kubectl rollout status deployment/pesitwizard-server -n pesitwizard

   # Check application version
   curl -sf https://api.pesit.example.com/actuator/info | jq .build

   # Check health
   curl -sf https://api.pesit.example.com/actuator/health | jq .
   ```

4. **Document**
   - Note the rolled-back version
   - Create bug ticket for investigation
   - Update deployment documentation

---

## Post-Incident Review

### Timeline Documentation

Document the incident timeline:

```
| Time (UTC) | Event |
|------------|-------|
| HH:MM | Alert fired: {alert name} |
| HH:MM | On-call acknowledged |
| HH:MM | {Action taken} |
| HH:MM | Root cause identified |
| HH:MM | Fix deployed |
| HH:MM | Service recovered |
| HH:MM | Incident closed |
```

### Post-Incident Review Template

```markdown
# Post-Incident Review: {Incident Title}

**Date**: {Date}
**Severity**: {SEV1-4}
**Duration**: {Start} - {End} ({Total time})
**Incident Commander**: {Name}

## Summary
{1-2 sentence summary of what happened and impact}

## Impact
- Users affected: {Number/percentage}
- Transfers impacted: {Number}
- Revenue impact: {If applicable}
- SLA impact: {If applicable}

## Timeline
{Detailed timeline}

## Root Cause
{Technical explanation of what caused the incident}

## Contributing Factors
- {Factor 1}
- {Factor 2}

## What Went Well
- {Positive 1}
- {Positive 2}

## What Could Be Improved
- {Improvement 1}
- {Improvement 2}

## Action Items
| Item | Owner | Due Date | Status |
|------|-------|----------|--------|
| {Action} | {Owner} | {Date} | {Status} |

## Lessons Learned
- {Lesson 1}
- {Lesson 2}
```

### Action Item Categories

1. **Detection**: Improve alerting, add missing monitors
2. **Response**: Update runbooks, improve tooling
3. **Prevention**: Fix root cause, add safeguards
4. **Process**: Improve communication, update procedures

### Review Meeting Agenda

1. **Timeline review** (10 min)
2. **Root cause analysis** (15 min)
3. **What went well** (5 min)
4. **What could improve** (10 min)
5. **Action items** (15 min)
6. **Follow-up schedule** (5 min)

---

## Appendix

### Useful Commands Cheat Sheet

```bash
# Pod status
kubectl get pods -n pesitwizard -o wide

# Pod logs
kubectl logs -l app=pesitwizard-server -n pesitwizard --tail=100

# Describe pod (events)
kubectl describe pod pesitwizard-server-0 -n pesitwizard

# Execute in pod
kubectl exec -it pesitwizard-server-0 -n pesitwizard -- bash

# Health check
curl -sf localhost:8080/actuator/health | jq .

# Metrics
curl -sf localhost:8080/actuator/prometheus

# Thread dump
curl -sf localhost:8080/actuator/threaddump | jq .

# Heap dump
kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
  jmap -dump:format=b,file=/tmp/heap.hprof 1

# Rolling restart
kubectl rollout restart deployment/pesitwizard-server -n pesitwizard

# Scale
kubectl scale deployment/pesitwizard-server --replicas=5 -n pesitwizard

# Helm rollback
helm rollback pesitwizard -n pesitwizard
```

### Contact List

| Role | Name | Email | Phone |
|------|------|-------|-------|
| On-call Primary | (PagerDuty) | | |
| Platform Lead | TBD | | |
| Database Admin | TBD | | |
| Security | TBD | | |
| Partner Relations | TBD | | |

### External Dependencies

| Service | Contact | Status Page |
|---------|---------|-------------|
| AWS/GCP/Azure | Support Portal | status.aws.amazon.com |
| Database (managed) | Support Portal | |
| Monitoring (Datadog/etc) | Support | |
| Partner: CX | partner@example.com | |
