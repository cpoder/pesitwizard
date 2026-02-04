# Client Usage

## Demo Video

This video shows the complete PeSIT Wizard Client workflow: adding a server, file transfer, favorites, calendar and scheduling.

<video controls width="100%" style="max-width: 800px; border-radius: 8px; margin: 1rem 0;">
  <source src="/videos/pesitwizard-demo.webm" type="video/webm">
  Your browser does not support the video tag.
</video>

## Web Interface

The web interface allows you to:
- Manage target PeSIT servers
- Send and receive files
- View transfer history
- Manage favorites and schedules
- Test connections

![Client Dashboard](/screenshots/client/dashboard.png)

### Send a File (SEND)

1. Go to **Transfer**
2. Select **SEND** as the direction
3. Select the target server
4. Fill in:
   - **Partner ID**: Your client identifier
   - **Local File Path**: Full path of the file to send
   - **Virtual File ID**: Virtual file identifier (provided by the bank)
5. Click **Start Transfer**

![Transfer Send](/screenshots/client/transfer-send.png)

### Receive a File (RECEIVE)

1. Go to **Transfer**
2. Select **RECEIVE** as the direction
3. Select the source server
4. Fill in:
   - **Partner ID**: Your client identifier
   - **Save To Path**: Destination path (supports placeholders)
   - **Virtual File ID**: Virtual file identifier
5. Click **Start Transfer**

![Transfer Receive](/screenshots/client/transfer-receive.png)

#### Path Placeholders

For RECEIVE transfers, you can use dynamic placeholders in the destination path:

| Placeholder | Description |
|-------------|-------------|
| `${partner}` | Partner ID |
| `${virtualFile}` | Virtual file name (PI 12) |
| `${server}` | Server ID |
| `${serverName}` | Server name |
| `${timestamp}` | Timestamp (yyyyMMdd_HHmmss) |
| `${date}` | Date (yyyyMMdd) |
| `${time}` | Time (HHmmss) |
| `${year}`, `${month}`, `${day}` | Date components |
| `${uuid}` | Unique UUID |

**Example**: `/data/received/${partner}/${virtualFile}_${timestamp}.dat`

Result: `/data/received/PARTNER01/DATA_FILE_20251211_213000.dat`

::: tip PeSIT Note
The PeSIT protocol does not transmit the physical filename, only the virtual file identifier (PI 12). The placeholders `${file}`, `${basename}`, `${ext}` are therefore not available.
:::

## REST API

### Send a File

```bash
curl -X POST http://localhost:8080/api/transfers/send \
  -H "Content-Type: multipart/form-data" \
  -F "file=@payment.xml" \
  -F "serverId=1" \
  -F "remoteFilename=PAYMENT_20250110.XML" \
  -F "partnerId=MY_COMPANY" \
  -F "virtualFile=PAYMENTS"
```

**Response**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "direction": "SEND",
  "filename": "PAYMENT_20250110.XML",
  "size": 15234,
  "startTime": "2025-01-10T10:30:00Z",
  "endTime": "2025-01-10T10:30:05Z"
}
```

### Receive a File

```bash
curl -X POST http://localhost:8080/api/transfers/receive \
  -H "Content-Type: application/json" \
  -d '{
    "serverId": 1,
    "remoteFilename": "STATEMENT_20250110.XML",
    "partnerId": "MY_COMPANY",
    "virtualFile": "STATEMENTS"
  }'
```

**Response**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "status": "COMPLETED",
  "direction": "RECEIVE",
  "filename": "STATEMENT_20250110.XML",
  "localPath": "/data/received/STATEMENT_20250110.XML",
  "size": 8542
}
```

### Download a Received File

```bash
curl -O http://localhost:8080/api/transfers/550e8400-e29b-41d4-a716-446655440001/download
```

### Transfer History

```bash
curl http://localhost:8080/api/transfers
```

**Response**:
```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "status": "COMPLETED",
      "direction": "SEND",
      "filename": "PAYMENT_20250110.XML",
      "serverName": "BNP Paribas",
      "startTime": "2025-01-10T10:30:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

### Filter History

```bash
# By status
curl "http://localhost:8080/api/transfers?status=FAILED"

# By direction
curl "http://localhost:8080/api/transfers?direction=SEND"

# By date
curl "http://localhost:8080/api/transfers?from=2025-01-01&to=2025-01-31"

# By server
curl "http://localhost:8080/api/transfers?serverId=1"
```

## Favorites

Favorites allow you to save transfer configurations for easy reuse.

![Favorites View](/screenshots/client/favorites.png)

### Create a Favorite

1. Perform a transfer from the **Transfer** or **History** page
2. Click the star icon to add to favorites
3. Give the favorite a name

### Edit a Favorite

1. Go to **Favorites**
2. Click the pencil icon
3. Modify the parameters:
   - Name and description
   - Target server
   - Partner ID
   - Virtual File
   - Local path (with placeholders for RECEIVE)
4. Click **Save Changes**

![Edit Favorite](/screenshots/client/favorite-edit.png)

::: info Schedule Synchronization
When you modify a favorite, all linked schedules are automatically updated.
:::

### Execute a Favorite

Click the **Execute** button to immediately start the transfer.

## Schedules

Schedules allow you to automate transfers at specific times.

![Schedules View](/screenshots/client/schedules.png)

### Create a Schedule

1. From the **Favorites** page, click the calendar icon
2. Choose the schedule type:
   - **Daily**: Every day at a specific time
   - **Weekly**: Each week on a specific day and time
   - **Monthly**: Each month on a specific day
   - **Hourly**: Every hour
   - **Interval**: Every N minutes
   - **Once**: A single time at a specific date/time
   - **Cron**: Custom cron expression
3. Configure the options:
   - **Working days only**: Skip weekends and holidays
   - **Business Calendar**: Use a custom calendar
4. Click **Create Schedule**

![Create Schedule](/screenshots/client/schedule-create.png)

### Business Calendars

Business calendars allow you to define working days and holidays for schedules.

![Calendars View](/screenshots/client/calendars.png)

#### Create a Calendar

1. Go to **Calendars**
2. Click **New Calendar**
3. Configure:
   - **Name**: Calendar name (e.g., "France")
   - **Timezone**: Time zone
   - **Working Days**: Working days (click to enable/disable)
   - **Holidays**: Add holidays
4. Click **Create Calendar**

![Calendar Form](/screenshots/client/calendar-form.png)

#### Use a Calendar

When creating a schedule, select the calendar in the **Business Calendar** field. Transfers will be automatically postponed to the next working day if the scheduled date falls on a holiday or weekend.

## Automation via Scripts

### Bash Script

```bash
#!/bin/bash
# send-payments.sh

API_URL="http://localhost:8080"
SERVER_ID=1
PARTNER_ID="MY_COMPANY"
VIRTUAL_FILE="PAYMENTS"

for file in /data/outbox/*.xml; do
  filename=$(basename "$file")
  echo "Sending $filename..."

  response=$(curl -s -X POST "$API_URL/api/transfers/send" \
    -F "file=@$file" \
    -F "serverId=$SERVER_ID" \
    -F "remoteFilename=$filename" \
    -F "partnerId=$PARTNER_ID" \
    -F "virtualFile=$VIRTUAL_FILE")

  status=$(echo "$response" | jq -r '.status')

  if [ "$status" = "COMPLETED" ]; then
    echo "OK: $filename sent successfully"
    mv "$file" /data/sent/
  else
    echo "FAIL: Failed to send $filename"
    echo "$response"
  fi
done
```

### Cron Job

```bash
# Retrieve statements every day at 7am
0 7 * * * /opt/scripts/receive-statements.sh >> /var/log/pesitwizard.log 2>&1

# Send payments every hour
0 * * * * /opt/scripts/send-payments.sh >> /var/log/pesitwizard.log 2>&1
```

## Error Codes

| Code | Description | Action |
|------|-------------|--------|
| `CONNECTION_REFUSED` | Server unreachable | Check host/port |
| `AUTH_FAILED` | Authentication failed | Check clientId/password |
| `PARTNER_UNKNOWN` | Partner not recognized | Check partnerId |
| `FILE_NOT_FOUND` | File not found | Check virtualFile/filename |
| `TIMEOUT` | Timeout exceeded | Retry or increase timeout |
