# Connect:Express interoperability

PeSIT Wizard is validated for interoperability against **IBM Sterling Connect:Express 1.5** (formerly
Axway CFT), which it can replace on either side of an exchange.

## What is tested

The interoperability suite (in the source repository, run in Docker) exercises, all green against
Connect:Express 1.5:

- **Transfers both ways** — Connect:Express → PeSIT Wizard and PeSIT Wizard → Connect:Express, from
  small files to multiple megabytes, with synchronisation points and concurrency, plus edge cases
  (empty / 1-byte files, spaces in names).
- **TLS both ways** — SSLPARM server mode, with and without the transport-length header
  (`TCPIP_HEADER`), PEM CA upload.
- **F.CANCEL, restart and replay** — cancellation mid-transfer and resumption.

## Interop notes

- **Transport header** — Connect:Express `SSLPARM TCPIP_HEADER` can be Y or N; set `tcpipHeader` on
  the listener / remote server to match.
- **Pre-connection** — Connect:Express partners of type T / O expect the 24-byte hors-SIT
  pre-connection message; configure `preconnectId` / `preconnectPassword`.
- **Restart** — restart works end to end PeSIT Wizard ↔ PeSIT Wizard. Connect:Express refuses a
  remote-requester-driven reprise of a CREATE (diagnostic D2-204), so a retry against Connect:Express
  automatically falls back to a full retransfer.
- **Record formats** — Connect:Express `TF` / `TV` text files keep their record boundaries; set
  `text: true` on the virtual file. For EBCDIC data, set `ebcdic: true`.

See the [protocol reference](/guide/reference/protocol) for the full feature list.
