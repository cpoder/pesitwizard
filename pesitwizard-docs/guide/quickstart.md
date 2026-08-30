# Quick start

PeSIT Wizard is a single binary (`pesitwizard`). There is nothing to license and no registry to log
in to — grab it from GitHub and run it.

## Run the node

```bash
PESIT_API_KEY=secret pesitwizard        # runs the node (default subcommand: serve)
```

One process exposes two REST surfaces backed by the same store, plus the web console:

- **Admin API** on `--api-port` (default 8080, `X-API-Key`): partners, virtual files, listeners,
  inbound transfer records, and the **web console at `/`**.
- **Transfer API** on `--transfer-port` (default 9081): remote servers,
  `/api/v1/transfers/send|receive|message`, outbound history.

Open `http://localhost:8080/`, paste the API key in the field at the top right, and the dashboard
populates.

### With Docker

```bash
docker run -p 8080:8080 -p 9081:9081 -p 5001:5001 \
  -e PESIT_API_KEY=secret \
  ghcr.io/pesitwizard/pesitwizard:latest
```

## Configure your first transfer

Everything the console does is a REST call. A minimal inbound setup — a partner, a virtual file to
receive into, and a listener:

```bash
KEY=secret
A=http://localhost:8080
H=(-H "X-API-Key: $KEY" -H 'Content-Type: application/json')

curl -s "${H[@]}" "$A/api/v1/config/partners" -X POST \
  -d '{"id":"BANK_A","enabled":true,"accessType":"BOTH"}'

curl -s "${H[@]}" "$A/api/v1/config/files" -X POST \
  -d '{"id":"INVOICES","enabled":true,"direction":"RECEIVE","recordLength":4096,"recordFormat":128}'

curl -s "${H[@]}" "$A/api/v1/servers" -X POST \
  -d '{"serverId":"PESIT-IN","port":5001,"receiveDirectory":"/data/received","sendDirectory":"/data/send","autoStart":true}'
```

Or provide a YAML bootstrap file with `PESIT_CONFIG=/path/config.yaml`:

```yaml
partners:
  - id: BANK_A
    enabled: true
    accessType: BOTH
files:
  - id: INVOICES
    enabled: true
    direction: RECEIVE
servers:
  - serverId: PESIT-IN
    port: 5001
    receiveDirectory: /data/received
    sendDirectory: /data/send
    autoStart: true
```

Sending `SIGHUP` re-reads that file and re-applies it without a restart.

## Send a one-shot transfer from the CLI

```bash
pesitwizard send    --host cx --port 5000 --server-id CETOM1 --partner PWSRV01 file.dat --remote PWRECV
pesitwizard receive --host cx --port 5000 --server-id CETOM1 --partner PWSRV01 PWSEND --file out.dat
```

Next: the [guide](/guide/).
