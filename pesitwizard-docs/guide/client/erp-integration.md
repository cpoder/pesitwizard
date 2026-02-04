# ERP Integration

## Overview

PeSIT Wizard exposes a REST API that makes it easy to integrate file transfers into your business processes.

## Typical Use Cases

### 1. Export Payments from the ERP

```
┌─────────┐     ┌─────────────┐     ┌─────────────────────┐     ┌────────┐
│   ERP   │────>│ XML File    │────>│ PeSIT Wizard Client  │────>│ Bank   │
└─────────┘     └─────────────┘     └─────────────────────┘     └────────┘
```

Your ERP generates a SEPA payment file, then calls the PeSIT Wizard API to send it.

### 2. Import Statements into Accounting

```
┌────────┐     ┌─────────────────────┐     ┌─────────────┐     ┌─────────────┐
│ Bank   │────>│ PeSIT Wizard Client  │────>│ XML File    │────>│ Accounting  │
└────────┘     └─────────────────────┘     └─────────────┘     └─────────────┘
```

PeSIT Wizard Client retrieves the statements, then your accounting software imports them.

## Webhook Integration

### Configuration

Configure a webhook to be notified of transfers:

```bash
curl -X POST http://localhost:8080/api/webhooks \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://my-erp.com/api/pesitwizard/callback",
    "events": ["TRANSFER_COMPLETED", "TRANSFER_FAILED"],
    "secret": "my-webhook-secret"
  }'
```

### Received Payload

```json
{
  "event": "TRANSFER_COMPLETED",
  "timestamp": "2025-01-10T10:30:05Z",
  "transfer": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "direction": "RECEIVE",
    "filename": "STATEMENT_20250110.XML",
    "localPath": "/data/received/STATEMENT_20250110.XML",
    "size": 8542
  }
}
```

### Signature Verification

```python
import hmac
import hashlib

def verify_webhook(payload, signature, secret):
    expected = hmac.new(
        secret.encode(),
        payload.encode(),
        hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(f"sha256={expected}", signature)
```

## Integration Examples

### Python

```python
import requests

class PesitClient:
    def __init__(self, base_url, api_key=None):
        self.base_url = base_url
        self.session = requests.Session()
        if api_key:
            self.session.headers['Authorization'] = f'Bearer {api_key}'

    def send_file(self, server_id, file_path, remote_name, partner_id, virtual_file):
        with open(file_path, 'rb') as f:
            response = self.session.post(
                f'{self.base_url}/api/transfers/send',
                files={'file': f},
                data={
                    'serverId': server_id,
                    'remoteFilename': remote_name,
                    'partnerId': partner_id,
                    'virtualFile': virtual_file
                }
            )
        return response.json()

    def receive_file(self, server_id, remote_name, partner_id, virtual_file):
        response = self.session.post(
            f'{self.base_url}/api/transfers/receive',
            json={
                'serverId': server_id,
                'remoteFilename': remote_name,
                'partnerId': partner_id,
                'virtualFile': virtual_file
            }
        )
        return response.json()

    def download_file(self, transfer_id, local_path):
        response = self.session.get(
            f'{self.base_url}/api/transfers/{transfer_id}/download',
            stream=True
        )
        with open(local_path, 'wb') as f:
            for chunk in response.iter_content(chunk_size=8192):
                f.write(chunk)

# Usage
client = PesitClient('http://localhost:8080')

# Send a payment
result = client.send_file(
    server_id=1,
    file_path='/data/payments/payment_20250110.xml',
    remote_name='PAYMENT_20250110.XML',
    partner_id='MY_COMPANY',
    virtual_file='PAYMENTS'
)
print(f"Transfer {result['status']}: {result['id']}")

# Receive a statement
result = client.receive_file(
    server_id=1,
    remote_name='STATEMENT_20250110.XML',
    partner_id='MY_COMPANY',
    virtual_file='STATEMENTS'
)
if result['status'] == 'COMPLETED':
    client.download_file(result['id'], '/data/statements/statement_20250110.xml')
```

### Node.js

```javascript
const axios = require('axios');
const FormData = require('form-data');
const fs = require('fs');

class PesitClient {
  constructor(baseUrl) {
    this.baseUrl = baseUrl;
  }

  async sendFile(serverId, filePath, remoteName, partnerId, virtualFile) {
    const form = new FormData();
    form.append('file', fs.createReadStream(filePath));
    form.append('serverId', serverId);
    form.append('remoteFilename', remoteName);
    form.append('partnerId', partnerId);
    form.append('virtualFile', virtualFile);

    const response = await axios.post(
      `${this.baseUrl}/api/transfers/send`,
      form,
      { headers: form.getHeaders() }
    );
    return response.data;
  }

  async receiveFile(serverId, remoteName, partnerId, virtualFile) {
    const response = await axios.post(
      `${this.baseUrl}/api/transfers/receive`,
      { serverId, remoteFilename: remoteName, partnerId, virtualFile }
    );
    return response.data;
  }
}

// Usage
const client = new PesitClient('http://localhost:8080');

async function main() {
  // Send
  const sendResult = await client.sendFile(
    1, './payment.xml', 'PAYMENT.XML', 'MY_COMPANY', 'PAYMENTS'
  );
  console.log('Send:', sendResult.status);

  // Receive
  const receiveResult = await client.receiveFile(
    1, 'STATEMENT.XML', 'MY_COMPANY', 'STATEMENTS'
  );
  console.log('Receive:', receiveResult.status);
}

main();
```

### Java

```java
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.http.*;

public class PesitClient {
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PesitClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
    }

    public TransferResult sendFile(int serverId, String filePath,
            String remoteName, String partnerId, String virtualFile) {

        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(filePath));
        body.add("serverId", serverId);
        body.add("remoteFilename", remoteName);
        body.add("partnerId", partnerId);
        body.add("virtualFile", virtualFile);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<TransferResult> response = restTemplate.exchange(
            baseUrl + "/api/transfers/send",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            TransferResult.class
        );
        return response.getBody();
    }
}
```

## Supported File Formats

| Format | Description | Usage |
|--------|-------------|-------|
| CFONB 160 | Domestic payments | Legacy |
| CFONB 240 | Account statements | Legacy |
| pain.001 | SEPA payments (XML) | Standard |
| pain.008 | SEPA direct debits (XML) | Standard |
| camt.053 | Account statements (XML) | Standard |

PeSIT Wizard transports files transparently - the format is handled by your ERP and your bank.
