# Clustering (NATS / JetStream)

Several nodes can form a cluster over a NATS server with JetStream enabled, so that configuration is
shared and one node is elected leader. Set `PESIT_CLUSTER_NATS` (e.g. `nats://nats:4222`) and, if you
run more than one cluster against the same NATS, `PESIT_CLUSTER_NAME`.

![Cluster](/screenshots/cluster.png)

## What the cluster does

- **Membership** — each node heartbeats into a JetStream KV bucket with a TTL; a node that stops is
  dropped when its key expires. `GET /api/v1/cluster` and the **Cluster** tab list the members. Each
  node advertises a reachable address via `PESIT_CLUSTER_ADVERTISE` (`host:port` of its admin API).
- **Leader election** — a KV lease: a node acquires the `leader` key atomically, renews it while
  alive, and the TTL lets another node take over on failure.
- **Configuration replication** — a change to a shared-policy object (partners, virtual files, remote
  partners and schedules) is published on NATS and applied by every other node. A joining node first
  requests a full snapshot from a peer and restores it, then follows the live stream. Listeners and
  connectors stay node-local.
- **Cluster-wide transfer history** — `GET /api/v1/cluster/transfers` aggregates the transfer records
  of every member.
- **Distributed schedules** — recurring jobs replicate across the cluster, and each node fires only
  the schedules it *owns* (a deterministic slice of the schedule id over the sorted live members), so
  a due job runs exactly once and the load spreads. A standalone node owns everything.

## Running a cluster

Point every node at the same NATS and give each a distinct `PESIT_NODE_ID` and advertise address:

```bash
PESIT_CLUSTER_NATS=nats://nats:4222 \
PESIT_CLUSTER_NAME=prod \
PESIT_NODE_ID=node-a \
PESIT_CLUSTER_ADVERTISE=node-a:8080 \
PESIT_API_KEY=secret pesitwizard
```

Configuration you create on any node replicates to the others; listeners and connectors are
configured per node.
