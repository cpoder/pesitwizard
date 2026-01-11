# PeSIT Client Integration Tests

This directory contains comprehensive integration tests for the PeSIT client, focusing on the restart mechanism and error handling.

## Test Files

### RestartMechanismIntegrationTest.java

Comprehensive tests for PeSIT restart mechanism functionality:

- **Test 1: Send with restart from sync point** - Simulates network failure during send, then resumes from last sync point
- **Test 2: Multiple interrupts and restarts** - Interrupts transfer multiple times and restarts each time
- **Test 3: Receive (PULL) with restart** - Tests resuming a download after interruption
- **Test 4: Restart from sync point 0** - Edge case: restart from the very beginning
- **Test 5: Large file restart (5MB)** - Tests restart mechanism with larger files
- **Test 6: Restart with varying chunk sizes** - Tests restart with different entity sizes (256B, 1KB)
- **Test 7: Restart at sync interval boundaries** - Tests restart exactly at sync interval boundaries

### RestartErrorHandlingTest.java

Error handling and edge case tests for restart scenarios:

- **Test 1: Restart with invalid negative sync point** - Tests server rejection of negative restart point
- **Test 2: Restart with out-of-range sync point** - Sync point beyond what was actually sent
- **Test 3: Restart with file size mismatch** - File size changes between interrupt and restart
- **Test 4: Restart without sync points enabled** - Attempts restart when sync points weren't negotiated
- **Test 5: Restart from sync 0 after partial** - Restart from beginning after partial send
- **Test 6: Restart with mismatched transfer ID** - Using different transfer ID for restart
- **Test 7: Restart after session timeout** - Tests restart after delay (server state persistence)

### CxQuickTest.java

Quick manual integration tests for basic operations:
- Push (send) operations
- Push with sync points
- Push with sync and restart
- Pull (receive) operations

## Prerequisites

### Server Requirements

These integration tests require a PeSIT server running with the following configuration:

1. **Server accessible at**: `localhost:5100` (default, configurable via system properties)
2. **Virtual files configured**:
   - `FILE` - Standard variable format file for general tests
   - `SYNCIN` - Fixed physical path file for restart tests (must persist between connections)
   - `BIG` - File for receive/pull tests
3. **Server capabilities**:
   - Sync points support (PI_07_SYNC_POINTS)
   - Resync/restart support (PI_18_POINT_RELANCE)
   - TLS support (optional, for TLS tests)

### System Properties

Configure test execution with system properties:

```bash
# Enable integration tests (required)
-Dpesit.integration.enabled=true

# Configure server connection (optional)
-Dpesit.test.host=localhost
-Dpesit.test.port=5100
-Dpesit.test.server=CETOM1
```

## Running the Tests

### Run All Restart Integration Tests

```bash
cd pesitwizard-client
mvn test -Dpesit.integration.enabled=true -Dtest="Restart*Test"
```

### Run Specific Test Class

```bash
# Run restart mechanism tests
mvn test -Dpesit.integration.enabled=true -Dtest=RestartMechanismIntegrationTest

# Run error handling tests
mvn test -Dpesit.integration.enabled=true -Dtest=RestartErrorHandlingTest
```

### Run Single Test Method

```bash
mvn test -Dpesit.integration.enabled=true \
  -Dtest=RestartMechanismIntegrationTest#testSendRestartFromSyncPoint
```

### Run with Custom Server

```bash
mvn test -Dpesit.integration.enabled=true \
  -Dpesit.test.host=pesit-server.example.com \
  -Dpesit.test.port=5000 \
  -Dpesit.test.server=BANK_SERVER \
  -Dtest=RestartMechanismIntegrationTest
```

## Test Coverage

The integration tests cover:

### Functional Scenarios
- ✅ Send with restart from sync point
- ✅ Receive with restart from sync point
- ✅ Multiple interrupts and restarts
- ✅ Restart from sync point 0 (beginning)
- ✅ Large file restart (multi-MB)
- ✅ Varying chunk sizes
- ✅ Sync interval boundary conditions

### Error Scenarios
- ✅ Invalid restart points (negative, out-of-range)
- ✅ File size mismatch between transfers
- ✅ Restart without sync points enabled
- ✅ Mismatched transfer IDs
- ✅ Session timeout between interrupt and restart

### Protocol Validation
- ✅ PI_18_POINT_RELANCE (restart point) handling
- ✅ PI_20_NUM_SYNC (sync number) tracking
- ✅ PI_07_SYNC_POINTS (sync interval) negotiation
- ✅ SYN/ACK_SYN message exchange
- ✅ IDT (interrupt) message handling
- ✅ Diagnostic code (PI_02_DIAG) validation

## Expected Results

### Successful Test Run

When tests pass, you should see output like:

```
=== TEST 1: SEND WITH RESTART FROM SYNC POINT ===
Phase 1: Partial send with interruption...
  Sync interval: 10 KB
  Sync 1 at 10240 bytes
  Sync 2 at 20480 bytes
  Sync 3 at 30720 bytes
  Sent 30720 bytes, last sync: 3
  Interrupted at sync point: 3

Phase 2: Resume from sync point 3...
  Resuming from byte offset: 30720
  Resumed and sent: 169984 bytes

✓✓✓ TEST 1 PASSED: Send restart successful ✓✓✓
```

### Common Issues

#### Tests Skipped
```
Assumptions.assumeTrue(INTEGRATION_ENABLED, ...)
```
**Solution**: Add `-Dpesit.integration.enabled=true`

#### Connection Refused
```
java.net.ConnectException: Connection refused
```
**Solution**: Ensure PeSIT server is running on configured host:port

#### File Not Found
```
Assumptions.assumeTrue(testFile.exists(), ...)
```
**Solution**: Configure virtual files on server or adjust test file paths

#### Timeout
```
java.net.SocketTimeoutException: Read timed out
```
**Solution**: Server may be slow or not responding. Check server logs and increase timeout if needed.

## Integration with CI/CD

### Skip Integration Tests by Default

In CI/CD pipelines, integration tests are skipped by default:

```bash
mvn clean verify
# Integration tests NOT executed
```

### Enable for Integration Test Stage

```bash
mvn clean verify -Dpesit.integration.enabled=true
# Integration tests executed
```

### Example GitHub Actions

```yaml
- name: Run Integration Tests
  run: |
    # Start PeSIT server in background
    docker run -d -p 5100:5100 pesitwizard-server:test

    # Wait for server ready
    sleep 10

    # Run integration tests
    mvn test -Dpesit.integration.enabled=true -Dtest="Restart*Test"
```

## Test Data

Tests use deterministic random data generation:

```java
new Random(42).nextBytes(data); // Fixed seed for reproducibility
```

This ensures:
- Consistent test behavior across runs
- Reproducible failures
- Verifiable data integrity (if server supports checksums)

## Performance Considerations

- **Large file tests (5MB)** may take 10-30 seconds depending on network
- **Multiple restart tests** create several connections (2-3 per test)
- **Total test suite** runs in approximately 2-5 minutes

To run faster subset:

```bash
# Run only small file tests
mvn test -Dpesit.integration.enabled=true \
  -Dtest=RestartMechanismIntegrationTest#testSendRestartFromSyncPoint
```

## Extending Tests

To add new restart mechanism tests:

1. Add test method to appropriate class
2. Use `@Order` annotation to control execution order
3. Follow naming pattern: `test<Scenario>` with `@DisplayName`
4. Use helper methods for common operations
5. Add comprehensive logging for debugging
6. Handle cleanup properly (even on failure)

Example:

```java
@Test
@Order(8)
@DisplayName("My new restart scenario")
void testMyNewRestartScenario() throws Exception {
    System.out.println("\n=== TEST 8: MY NEW SCENARIO ===");

    // Test implementation

    System.out.println("\n✓✓✓ TEST 8 COMPLETED ✓✓✓");
}
```

## Debugging Failed Tests

Enable verbose output:

```bash
mvn test -Dpesit.integration.enabled=true -X -Dtest=RestartMechanismIntegrationTest
```

Check logs for:
- Connection establishment
- Sync point negotiations
- FPDU exchanges
- Diagnostic codes
- Server responses

## References

- PeSIT Specification Version E (September 1989)
- [CLAUDE.md](/CLAUDE.md) - Architecture documentation
- [pesitwizard-pesit README](/pesitwizard-pesit/README.md) - Protocol library
