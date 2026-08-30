# PeSIT E protocol

PeSIT Wizard implements **PeSIT E** — the TLS-capable version of the protocol used on the SIT network
and by IBM Sterling Connect:Express — and is validated for interoperability against Connect:Express
1.5.

## What the node implements

| Feature | Notes |
|---------|-------|
| **CRC** (PI 1) | Fletcher checksum negotiated in CONNECT / ACONNECT, verified on every entity. |
| **Compression** (PI 21) | Horizontal / vertical / mixed, negotiated in ORF / ACK(ORF). |
| **Multi-article DTF** | Up to 255 articles per DTF, entities filled up to the negotiated size (PI 25). |
| **Segmentation** | DTFDA / DTFMA / DTFFA carry one article that spans several entities. |
| **Synchronisation points** | With a window (PI 7 byte 3); asynchronous acknowledgement. |
| **Restart / resynchronisation** | Persistent per-transfer checkpoints (sync number ↔ byte offset ↔ article count), correct PI 13 / 15 / 18 on both sides. |
| **Record formats** | Binary undefined / fixed / variable, text variable / fixed. |
| **EBCDIC** (PI 16) | CP037 translation of article bytes on the wire. |
| **Extra PIs** | Label (PI 37), SIT client / bank ids (PI 61 / 62) and free message (PI 99) on requests. |
| **Diagnostics** | The full Annex D catalogue with Connect:Express-compatible texts, plus PI 29 complements. |
| **TLS** | With or without a transport-length header (`TCPIP_HEADER`), mTLS, PEM material. |
| **Pre-connection** | The 24-byte EBCDIC hors-SIT message, for Connect:Express partners of type T / O. |
| **Clean cancellation** | F.CANCEL (IDT) then CRF / DESELECT / RELEASE. |

## Record formats

An article is one logical record. On the wire:

- **Binary undefined (`BU`)** — cut into articles of the record length; the last may be shorter. The
  default for arbitrary files.
- **Binary fixed (`BF`)** — every article is exactly the record length.
- **Text variable (`TV`)** — one article per line; the line feed is stripped on send and appended on
  receive.
- **Text fixed (`TF`)** — like `TV`, padded with spaces to the record length.

Set `text: true` on a virtual file for text records, and `ebcdic: true` to translate them to EBCDIC
CP037 on the wire.

## State machine

The session engines are driven by the protocol **state tables as data** (the same shape as the
Connect:Express tables), shared by the listening and initiating roles. This is what lets the node
handle RESYN / IDT collisions, windowed synchronisation and restart correctly.

For the full parameter catalogue and state tables, see the
[source documentation](https://github.com/pesitwizard/pesitwizard-rs/tree/main/docs).
