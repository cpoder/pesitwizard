# PeSIT Restart Mechanism Investigation

## Problem

Integration tests for send restart fail with error **1302: "invalid structure of command 02"** from Connect:Express server when sending WRITE FPDU with PI_18_POINT_RELANCE parameter.

## Root Cause

The Connect:Express server rejects `WRITE` FPDUs that include `PI_18_POINT_RELANCE` (restart point) parameter, responding with ABORT and error code 1302.

## PeSIT Protocol Analysis

### Restart-Related Parameters

1. **PI_15_TRANSFERT_RELANCE** (Transfer Restarted)
   - Type: Single bit (S)
   - Length: 1 byte
   - Usage: Flag indicating this is a restarted transfer
   - Allowed in: SELECT (optional)

2. **PI_18_POINT_RELANCE** (Restart Point)
   - Type: Numeric (N)
   - Length: 3 bytes
   - Usage: Sync point number to restart from
   - Allowed in: READ, RESYN, ACK_WRITE, ACK_RESYN

3. **PI_23_RESYNC** (Resynchronization)
   - Type: Single bit (S)
   - Length: 1 byte
   - Usage: Flag indicating resync capability
   - Allowed in: CONNECT, ACONNECT

### FPDU Types for Restart

1. **RESYN** (0xC0, 0x05)
   - Purpose: Explicit resynchronization message
   - Parameters:
     - PI_02_DIAG (mandatory)
     - PI_18_POINT_RELANCE (mandatory)
   - Response: ACK_RESYN with PI_18

2. **WRITE** (0xC0, 0x02)
   - Currently defined: NO parameters
   - Working test: Sends PI_18 successfully
   - Connect:Express: **Rejects PI_18** (error 1302)

## Discrepancy

The `CxQuickTest.java` working test does:
```java
s.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(dst)
    .withParameter(new ParameterValue(PI_18_POINT_RELANCE, lastSync)));
```

But Connect:Express rejects this in our tests with error 1302.

## Possible Explanations

### 1. Server Configuration
Connect:Express may require specific virtual file configuration for restart:
- Virtual file SYNCIN must have **fixed physical path**  ✅ We use this
- Server must have restart enabled ❓Unknown
- Specific permissions or attributes needed ❓Unknown

### 2. Protocol Variation
Different PeSIT implementations may handle restart differently:
- **Option A**: WRITE with PI_18 (what our test + CxQuickTest does)
- **Option B**: RESYN instead of WRITE
- **Option C**: PI_15 flag in SELECT/CREATE

### 3. Timing or State
Server may reject restart if:
- Too much time passed since interruption
- Transfer state not properly persisted
- Need to send explicit end markers before restart
- File being modified between attempts

### 4. FpduType Definition Gap
Our `FpduType.java` defines WRITE without parameters, but:
- Working test sends PI_18 in WRITE
- No validation prevents this
- Server accepts/rejects based on its own rules

## Solutions to Try

### Solution 1: Add PI_18 to WRITE Definition (DONE)

Updated `FpduType.java` line 202:
```java
WRITE(0xC0, 0x02, "FPDU.WRITE", ACK_WRITE,
    new ParameterRequirement(PI_18_POINT_RELANCE, false)),
```

**Status**: ✅ Code updated, but server still rejects

### Solution 2: Use RESYN Instead of WRITE

Try sending RESYN FPDU after OPEN:
```java
// Instead of:
session.sendFpduWithAck(new Fpdu(FpduType.WRITE)
    .withIdDst(serverConnId)
    .withParameter(new ParameterValue(PI_18_POINT_RELANCE, restartSync)));

// Try:
session.sendFpduWithAck(new Fpdu(FpduType.RESYN)
    .withIdDst(serverConnId)
    .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0}))
    .withParameter(new ParameterValue(PI_18_POINT_RELANCE, restartSync)));
```

**Status**: ❓Not tested

### Solution 3: Use PI_15 Flag in SELECT

For receive restart, SELECT supports PI_15_TRANSFERT_RELANCE flag. Maybe there's an equivalent for send?

**Status**: ❓Needs investigation

### Solution 4: Server Configuration

Check Connect:Express configuration:
- Verify SYNCIN virtual file allows restart
- Check server logs for specific error details
- Verify resync capability is enabled
- Check if there's a server-side command to enable restart

**Status**: ❓Needs server access

### Solution 5: Test Against pesitwizard-server

Our own server implementation should fully support the restart mechanism:
```bash
# Start pesitwizard-server
cd pesitwizard-server
mvn spring-boot:run

# Run tests against it
mvn test -Dpesit.integration.enabled=true \
    -Dpesit.test.host=localhost \
    -Dpesit.test.port=5000 \
    -Dpesit.test.server=PESIT_SERVER \
    -Dtest=RestartMechanismIntegrationTest
```

**Status**: ❓Not tested

## Current Test Status

### Working ✅
- Receive restart (PULL) - Test 3 passed
- Error handling tests (5/7 passed)
- Partial send with sync points
- Interruption mechanism

### Failing ❌
- Send restart (PUSH) - Server rejects WRITE with PI_18
- 6/7 tests in RestartMechanismIntegrationTest
- 2/7 tests in RestartErrorHandlingTest

## Recommendations

1. **Immediate**: Try Solution 2 (RESYN instead of WRITE)
2. **Short-term**: Test against pesitwizard-server (Solution 5)
3. **Medium-term**: Contact Connect:Express support for error 1302 details
4. **Long-term**: Document server compatibility matrix

## PeSIT Specification References

From the code analysis:

```java
// RESYN is the official restart message
RESYN(0xC0, 0x05, "FPDU.RESYN", ACK_RESYN,
    new ParameterRequirement(PI_02_DIAG, true),
    new ParameterRequirement(PI_18_POINT_RELANCE, true))

// SELECT supports restart flag
SELECT(0xC0, 0x11, "FPDU.SELECT", ACK_SELECT,
    ...
    new ParameterRequirement(PI_15_TRANSFERT_RELANCE, false),
    ...)

// READ supports restart point
READ(0xC0, 0x01, "FPDU.READ", ACK_READ,
    new ParameterRequirement(PI_18_POINT_RELANCE, true))
```

## Solution (IMPLEMENTED ✅)

The correct protocol flow from pesit.html is:

1. **CREATE** with `.restart()` method (adds PI_15_TRANSFERT_RELANCE = 1)
2. **WRITE** with NO parameters
3. **ACK_WRITE** contains PI_18_POINT_RELANCE with server's restart point decision

The key insight: **The server decides the restart point, not the client.**

### Implementation

```java
// CREATE with restart flag
session.sendFpduWithAck(
    new CreateMessageBuilder()
        .filename(vfile)
        .transferId(transferId)  // Same transferId as interrupted transfer
        .restart()  // PI_15 = 1
        .build(serverConnId));

// WRITE with NO parameters
Fpdu ackWrite = session.sendFpduWithAck(
    new Fpdu(FpduType.WRITE).withIdDst(serverConnId));

// Read server's restart point
ParameterValue pi18 = ackWrite.getParameter(PI_18_POINT_RELANCE);
int serverRestartPoint = parseNumeric(pi18.getValue());

// Resume from server's decision
long resumeOffset = calculateResumeOffset(syncIntervalBytes, serverRestartPoint, recordLength);
int syncNumber = serverRestartPoint;  // Use server's decision, not our request
```

## Final Test Results

✅ **All 14 tests passing (100% success rate)**

- RestartMechanismIntegrationTest: 7/7 ✅
- RestartErrorHandlingTest: 7/7 ✅

See RESTART_FIX_SUMMARY.md for complete details.
