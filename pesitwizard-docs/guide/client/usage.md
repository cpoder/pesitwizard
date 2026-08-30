# Partners & transfers

The node initiates transfers as well as accepting them. Outgoing transfers are driven from the
**Send / Receive** tab, the transfer API, or the CLI.

## Remote servers

Define the servers this node connects out to in the **Remote servers** tab (`/api/v1/servers` on the
transfer API): host, port, server id (PI 4), transport (plain / TLS, optional transport-length
header), and optionally a partner id, password and pre-connection credentials. You can test
connectivity and mark a default server.

## Sending and receiving

From the **Send / Receive** tab (or `POST /api/v1/transfers/send|receive`): pick a remote server, a
partner id (PI 3), local and virtual file names, and options — synchronisation points, compression,
text mode, EBCDIC, and an optional label (PI 37) and free message (PI 99).

```bash
# send a file
curl -s -H 'Content-Type: application/json' \
  http://localhost:9081/api/v1/transfers/send -X POST \
  -d '{"server":"bank-a","partnerId":"PWSRV01","filename":"/data/send/sepa.dat","remoteFilename":"PAYMENTS"}'
```

The **Transfers** tab shows inbound and outbound records with live status, progress and cancel /
retry. Interrupted transfers can be resumed from their last checkpoint.

## Scheduled transfers

Recurring send / receive jobs are managed in the **Schedules** tab (`/api/v1/schedules`). Each job
runs on a fixed **interval** or a **cron expression** (5, 6 or 7 fields, e.g. `0 2 * * *` for daily
at 02:00). Across a [cluster](/guide/server/clustering) they replicate and are distributed by
ownership, so a due job fires exactly once and the load spreads.

## One-shot CLI

The same binary can run a single transfer without the running node's API:

```bash
pesitwizard send    --host cx --port 5000 --server-id CETOM1 --partner PWSRV01 file.dat --remote PWRECV
pesitwizard receive --host cx --port 5000 --server-id CETOM1 --partner PWSRV01 PWSEND --file out.dat
pesitwizard message --host cx --port 5000 --server-id CETOM1 --partner PWSRV01 "hello" --reply
```
