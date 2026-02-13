package com.pesitwizard.channel;

import static org.junit.jupiter.api.Assertions.*;

import com.pesitwizard.fpdu.*;
import java.io.*;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for TCP and TLS PeSIT channel framing. Verifies that: - Bytes written by a channel can be
 * read back by the same channel type - TCP wire format has an external 2-byte length prefix + FPDU
 * - TLS wire format has just the FPDU (with its internal 2-byte length) - Both channels produce the
 * same logical FPDU after read - FpduReader/FpduParser work correctly with both channel types -
 * ACK_WRITE (the critical FPDU for CX TLS integration) is identical via both channels
 */
class PesitChannelTest {

    // Build a minimal CONNECT FPDU for testing (with all mandatory PIs)
    private byte[] buildConnectFpdu() {
        return FpduBuilder.buildFpdu(
                FpduType.CONNECT,
                0,
                99,
                new ParameterValue(ParameterIdentifier.PI_03_DEMANDEUR, "TEST"),
                new ParameterValue(ParameterIdentifier.PI_04_SERVEUR, "SRVR"),
                new ParameterValue(ParameterIdentifier.PI_06_VERSION, 2),
                new ParameterValue(ParameterIdentifier.PI_22_TYPE_ACCES, 2));
    }

    // Build a minimal ACK_WRITE FPDU (the one that fails in CX TLS transfers)
    private byte[] buildAckWriteFpdu() {
        return FpduBuilder.buildFpdu(
                FpduType.ACK_WRITE,
                98,
                0,
                new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] {0, 0, 0}),
                new ParameterValue(ParameterIdentifier.PI_18_POINT_RELANCE, new byte[] {0, 0, 0}));
    }

    // Build a WRITE FPDU (no mandatory parameters)
    private byte[] buildWriteFpdu() {
        return FpduBuilder.buildFpdu(FpduType.WRITE, 1, 0);
    }

    // Build a DTF FPDU with data
    private byte[] buildDtfFpdu() {
        byte[] payload = "Hello PeSIT".getBytes();
        return FpduBuilder.buildFpdu(FpduType.DTF, 1, 1, payload);
    }

    // Helper to format bytes as hex for assertions
    private String hex(byte[] data) {
        return HexFormat.ofDelimiter(" ").formatHex(data).toUpperCase();
    }

    @Nested
    @DisplayName("TCP wire format")
    class TcpWireFormat {

        @Test
        @DisplayName("writeFrame adds external 2-byte length prefix")
        void writeFrameAddsExternalLengthPrefix() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            TcpPesitChannel channel = new TcpPesitChannel(null, out);

            byte[] fpdu = buildConnectFpdu();
            channel.writeFrame(fpdu);

            byte[] wire = baos.toByteArray();
            // TCP adds 2-byte external prefix
            assertEquals(
                    fpdu.length + 2,
                    wire.length,
                    "TCP wire should be FPDU length + 2 (external prefix)");

            // External prefix should equal FPDU length
            int extLen = ((wire[0] & 0xFF) << 8) | (wire[1] & 0xFF);
            assertEquals(
                    fpdu.length, extLen, "External length prefix should equal FPDU data length");

            // Remaining bytes should be the FPDU data
            byte[] fpduOnWire = Arrays.copyOfRange(wire, 2, wire.length);
            assertArrayEquals(
                    fpdu, fpduOnWire, "FPDU data after external prefix should match original");
        }

        @Test
        @DisplayName("readFrame strips external prefix and returns FPDU with internal length")
        void readFrameStripsExternalPrefix() throws IOException {
            byte[] fpdu = buildConnectFpdu();

            // Build TCP wire format: [ext_len(2)][fpdu_data]
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream writer = new DataOutputStream(baos);
            writer.writeShort(fpdu.length);
            writer.write(fpdu);

            DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
            TcpPesitChannel channel = new TcpPesitChannel(in, null);

            byte[] result = channel.readFrame();
            assertArrayEquals(
                    fpdu,
                    result,
                    "readFrame should return FPDU data (with internal length, without external prefix)");
        }

        @Test
        @DisplayName("write then read round-trip preserves FPDU")
        void writeReadRoundTrip() throws IOException {
            byte[] fpdu = buildAckWriteFpdu();

            // Write
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            TcpPesitChannel writeChannel = new TcpPesitChannel(null, out);
            writeChannel.writeFrame(fpdu);

            // Read back
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
            TcpPesitChannel readChannel = new TcpPesitChannel(in, null);
            byte[] result = readChannel.readFrame();

            assertArrayEquals(fpdu, result, "TCP round-trip should preserve FPDU data");
        }

        @Test
        @DisplayName("writeFpdu builds and frames correctly")
        void writeFpduBuildsAndFrames() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            TcpPesitChannel channel = new TcpPesitChannel(null, out);

            Fpdu fpdu =
                    new Fpdu(FpduType.ACK_WRITE)
                            .withIdDst(98)
                            .withIdSrc(0)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_02_DIAG, new byte[] {0, 0, 0}))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_18_POINT_RELANCE,
                                            new byte[] {0, 0, 0}));

            channel.writeFpdu(fpdu);

            byte[] wire = baos.toByteArray();
            byte[] expected = FpduBuilder.buildFpdu(fpdu);

            // Wire should be ext_len(2) + FPDU
            assertEquals(expected.length + 2, wire.length);

            // Verify we can read it back
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(wire));
            TcpPesitChannel readChannel = new TcpPesitChannel(in, null);
            byte[] result = readChannel.readFrame();
            assertArrayEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("TLS wire format")
    class TlsWireFormat {

        @Test
        @DisplayName("writeFrame writes FPDU directly without external prefix")
        void writeFrameNoExternalPrefix() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            TlsPesitChannel channel = new TlsPesitChannel(null, out);

            byte[] fpdu = buildConnectFpdu();
            channel.writeFrame(fpdu);

            byte[] wire = baos.toByteArray();
            // TLS writes FPDU directly - no external prefix
            assertEquals(
                    fpdu.length,
                    wire.length,
                    "TLS wire should equal FPDU length (no external prefix)");
            assertArrayEquals(fpdu, wire, "TLS wire should be the raw FPDU data");
        }

        @Test
        @DisplayName("readFrame reads internal length and reconstructs full FPDU")
        void readFrameReadsInternalLength() throws IOException {
            byte[] fpdu = buildConnectFpdu();

            // TLS wire: just the FPDU as-is (starts with internal length)
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(fpdu));
            TlsPesitChannel channel = new TlsPesitChannel(in, null);

            byte[] result = channel.readFrame();
            assertArrayEquals(
                    fpdu,
                    result,
                    "TLS readFrame should return full FPDU including internal length");
        }

        @Test
        @DisplayName("write then read round-trip preserves FPDU")
        void writeReadRoundTrip() throws IOException {
            byte[] fpdu = buildAckWriteFpdu();

            // Write
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            TlsPesitChannel writeChannel = new TlsPesitChannel(null, out);
            writeChannel.writeFrame(fpdu);

            // Read back
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
            TlsPesitChannel readChannel = new TlsPesitChannel(in, null);
            byte[] result = readChannel.readFrame();

            assertArrayEquals(fpdu, result, "TLS round-trip should preserve FPDU data");
        }

        @Test
        @DisplayName("writeFpdu builds and writes correctly")
        void writeFpduBuildsAndWrites() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            TlsPesitChannel channel = new TlsPesitChannel(null, out);

            Fpdu fpdu =
                    new Fpdu(FpduType.ACK_WRITE)
                            .withIdDst(98)
                            .withIdSrc(0)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_02_DIAG, new byte[] {0, 0, 0}))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_18_POINT_RELANCE,
                                            new byte[] {0, 0, 0}));

            channel.writeFpdu(fpdu);

            byte[] wire = baos.toByteArray();
            byte[] expected = FpduBuilder.buildFpdu(fpdu);

            // TLS writes FPDU directly
            assertArrayEquals(expected, wire, "TLS writeFpdu should write FPDU bytes directly");

            // Verify we can read it back
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(wire));
            TlsPesitChannel readChannel = new TlsPesitChannel(in, null);
            byte[] result = readChannel.readFrame();
            assertArrayEquals(expected, result);
        }

        @Test
        @DisplayName("readFrame rejects FPDU with length < 6")
        void readFrameRejectsShortFpdu() throws IOException {
            // Write an invalid FPDU with length = 4 (too short)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream writer = new DataOutputStream(baos);
            writer.writeShort(4); // length < 6
            writer.writeShort(0); // dummy bytes

            DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
            TlsPesitChannel channel = new TlsPesitChannel(in, null);

            IOException e = assertThrows(IOException.class, () -> channel.readFrame());
            assertTrue(
                    e.getMessage().contains("Invalid FPDU length"),
                    "Should reject FPDU with length < 6");
        }

        @Test
        @DisplayName("readFrame enforces max length")
        void readFrameEnforcesMaxLength() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream writer = new DataOutputStream(baos);
            writer.writeShort(1000); // Claim length of 1000
            writer.write(new byte[998]); // Provide the data

            DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
            TlsPesitChannel channel = new TlsPesitChannel(in, null);

            IOException e = assertThrows(IOException.class, () -> channel.readFrame(100));
            assertTrue(
                    e.getMessage().contains("exceeds max"),
                    "Should reject FPDU exceeding max length");
        }
    }

    @Nested
    @DisplayName("Cross-channel compatibility")
    class CrossChannelCompatibility {

        @Test
        @DisplayName("TCP and TLS produce same FPDU data for readFrame")
        void bothChannelsProduceSameFpduData() throws IOException {
            byte[] fpdu = buildAckWriteFpdu();

            // TCP: write with external prefix, read strips it
            ByteArrayOutputStream tcpBaos = new ByteArrayOutputStream();
            DataOutputStream tcpOut = new DataOutputStream(tcpBaos);
            new TcpPesitChannel(null, tcpOut).writeFrame(fpdu);

            DataInputStream tcpIn =
                    new DataInputStream(new ByteArrayInputStream(tcpBaos.toByteArray()));
            byte[] tcpResult = new TcpPesitChannel(tcpIn, null).readFrame();

            // TLS: write directly, read uses internal length
            ByteArrayOutputStream tlsBaos = new ByteArrayOutputStream();
            DataOutputStream tlsOut = new DataOutputStream(tlsBaos);
            new TlsPesitChannel(null, tlsOut).writeFrame(fpdu);

            DataInputStream tlsIn =
                    new DataInputStream(new ByteArrayInputStream(tlsBaos.toByteArray()));
            byte[] tlsResult = new TlsPesitChannel(tlsIn, null).readFrame();

            assertArrayEquals(
                    tcpResult,
                    tlsResult,
                    "Both channels should produce identical FPDU data from readFrame");
            assertArrayEquals(fpdu, tcpResult, "FPDU data should match original");
        }

        @Test
        @DisplayName("FpduParser parses same result from both channel types")
        void fpduParserSameResultFromBothChannels() throws IOException {
            Fpdu original =
                    new Fpdu(FpduType.ACK_WRITE)
                            .withIdDst(98)
                            .withIdSrc(0)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_02_DIAG, new byte[] {0, 0, 0}))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_18_POINT_RELANCE,
                                            new byte[] {0, 0, 0}));
            byte[] fpduBytes = FpduBuilder.buildFpdu(original);

            // TCP round-trip
            ByteArrayOutputStream tcpBaos = new ByteArrayOutputStream();
            new TcpPesitChannel(null, new DataOutputStream(tcpBaos)).writeFrame(fpduBytes);
            byte[] tcpRead =
                    new TcpPesitChannel(
                                    new DataInputStream(
                                            new ByteArrayInputStream(tcpBaos.toByteArray())),
                                    null)
                            .readFrame();
            Fpdu tcpParsed = new FpduParser(tcpRead).parse();

            // TLS round-trip
            ByteArrayOutputStream tlsBaos = new ByteArrayOutputStream();
            new TlsPesitChannel(null, new DataOutputStream(tlsBaos)).writeFrame(fpduBytes);
            byte[] tlsRead =
                    new TlsPesitChannel(
                                    new DataInputStream(
                                            new ByteArrayInputStream(tlsBaos.toByteArray())),
                                    null)
                            .readFrame();
            Fpdu tlsParsed = new FpduParser(tlsRead).parse();

            assertEquals(tcpParsed.getFpduType(), tlsParsed.getFpduType());
            assertEquals(tcpParsed.getIdDst(), tlsParsed.getIdDst());
            assertEquals(tcpParsed.getIdSrc(), tlsParsed.getIdSrc());
            assertEquals(tcpParsed.getParameters().size(), tlsParsed.getParameters().size());
        }

        @Test
        @DisplayName("FpduReader works identically with both channels")
        void fpduReaderWorksSameWithBothChannels() throws IOException {
            byte[] fpduBytes = buildConnectFpdu();

            // TCP: write, then read via FpduReader
            ByteArrayOutputStream tcpBaos = new ByteArrayOutputStream();
            new TcpPesitChannel(null, new DataOutputStream(tcpBaos)).writeFrame(fpduBytes);
            TcpPesitChannel tcpChannel =
                    new TcpPesitChannel(
                            new DataInputStream(new ByteArrayInputStream(tcpBaos.toByteArray())),
                            null);
            FpduReader tcpReader = new FpduReader(tcpChannel);
            Fpdu tcpFpdu = tcpReader.read();

            // TLS: write, then read via FpduReader
            ByteArrayOutputStream tlsBaos = new ByteArrayOutputStream();
            new TlsPesitChannel(null, new DataOutputStream(tlsBaos)).writeFrame(fpduBytes);
            TlsPesitChannel tlsChannel =
                    new TlsPesitChannel(
                            new DataInputStream(new ByteArrayInputStream(tlsBaos.toByteArray())),
                            null);
            FpduReader tlsReader = new FpduReader(tlsChannel);
            Fpdu tlsFpdu = tlsReader.read();

            assertEquals(tcpFpdu.getFpduType(), tlsFpdu.getFpduType());
            assertEquals(tcpFpdu.getIdDst(), tlsFpdu.getIdDst());
            assertEquals(tcpFpdu.getIdSrc(), tlsFpdu.getIdSrc());
        }

        @Test
        @DisplayName("TLS-written FPDU readable by TLS reader (simulates CX->PW Server)")
        void tlsReadsFpduWrittenByTls() throws IOException {
            // CX sends over TLS (no external prefix)
            byte[] fpdu = buildConnectFpdu();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new TlsPesitChannel(null, new DataOutputStream(baos)).writeFrame(fpdu);
            byte[] tlsWire = baos.toByteArray();

            // PW Server reads using TLS channel
            TlsPesitChannel tlsChannel =
                    new TlsPesitChannel(
                            new DataInputStream(new ByteArrayInputStream(tlsWire)), null);
            byte[] result = tlsChannel.readFrame();

            assertArrayEquals(fpdu, result);

            // Verify parsed FPDU
            Fpdu parsed = new FpduParser(result).parse();
            assertEquals(FpduType.CONNECT, parsed.getFpduType());
            assertEquals(99, parsed.getIdSrc());
        }
    }

    @Nested
    @DisplayName("Wire format byte-level verification")
    class WireFormatByteLevel {

        @Test
        @DisplayName("TCP ACK_WRITE wire format matches expected bytes")
        void tcpAckWriteWireFormat() throws IOException {
            byte[] fpdu = buildAckWriteFpdu();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new TcpPesitChannel(null, new DataOutputStream(baos)).writeFrame(fpdu);
            byte[] wire = baos.toByteArray();

            // Verify internal FPDU length
            int internalLen = ((fpdu[0] & 0xFF) << 8) | (fpdu[1] & 0xFF);
            assertEquals(
                    fpdu.length,
                    internalLen,
                    "Internal FPDU length should equal byte array length");

            // Verify external length prefix
            int externalLen = ((wire[0] & 0xFF) << 8) | (wire[1] & 0xFF);
            assertEquals(
                    fpdu.length, externalLen, "External length prefix should equal FPDU length");

            // Verify phase and type
            assertEquals((byte) 0xC0, wire[4], "Phase byte (after ext+int length)");
            assertEquals((byte) 0x36, wire[5], "Type byte for ACK_WRITE");
        }

        @Test
        @DisplayName("TLS ACK_WRITE wire format matches expected bytes")
        void tlsAckWriteWireFormat() throws IOException {
            byte[] fpdu = buildAckWriteFpdu();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new TlsPesitChannel(null, new DataOutputStream(baos)).writeFrame(fpdu);
            byte[] wire = baos.toByteArray();

            // No external prefix - wire IS the FPDU
            assertArrayEquals(fpdu, wire);

            // Verify internal length
            int internalLen = ((wire[0] & 0xFF) << 8) | (wire[1] & 0xFF);
            assertEquals(wire.length, internalLen, "Internal FPDU length should equal wire length");

            // Verify phase and type at positions 2,3 (right after internal length)
            assertEquals((byte) 0xC0, wire[2], "Phase byte");
            assertEquals((byte) 0x36, wire[3], "Type byte for ACK_WRITE");
        }

        @Test
        @DisplayName("TCP wire has double length prefix, TLS has single")
        void tcpDoubleLengthTlsSingle() throws IOException {
            byte[] fpdu = buildAckWriteFpdu();

            // TCP wire
            ByteArrayOutputStream tcpBaos = new ByteArrayOutputStream();
            new TcpPesitChannel(null, new DataOutputStream(tcpBaos)).writeFrame(fpdu);
            byte[] tcpWire = tcpBaos.toByteArray();

            // TLS wire
            ByteArrayOutputStream tlsBaos = new ByteArrayOutputStream();
            new TlsPesitChannel(null, new DataOutputStream(tlsBaos)).writeFrame(fpdu);
            byte[] tlsWire = tlsBaos.toByteArray();

            // TCP = 2 extra bytes (external prefix)
            assertEquals(
                    tlsWire.length + 2,
                    tcpWire.length,
                    "TCP wire should be 2 bytes longer than TLS (external prefix)");

            // TCP wire[2:] should equal TLS wire (the FPDU itself)
            assertArrayEquals(
                    tlsWire,
                    Arrays.copyOfRange(tcpWire, 2, tcpWire.length),
                    "TCP wire after external prefix should equal TLS wire");
        }

        @Test
        @DisplayName("ACK_WRITE FPDU structure decoded correctly")
        void ackWriteFpduStructureDecoded() throws IOException {
            byte[] fpdu = buildAckWriteFpdu();

            System.out.println("ACK_WRITE raw bytes: " + hex(fpdu));
            System.out.println("ACK_WRITE length: " + fpdu.length);

            // Parse and verify structure
            Fpdu parsed = new FpduParser(fpdu).parse();
            assertEquals(FpduType.ACK_WRITE, parsed.getFpduType());
            assertEquals(98, parsed.getIdDst());
            assertEquals(0, parsed.getIdSrc());
            assertEquals(2, parsed.getParameters().size());

            // Verify PI_02 (DIAG)
            ParameterValue pi02 = parsed.getParameter(ParameterIdentifier.PI_02_DIAG);
            assertNotNull(pi02, "PI_02_DIAG should be present");
            assertArrayEquals(
                    new byte[] {0, 0, 0}, pi02.getValue(), "PI_02 should be all zeros (OK)");

            // Verify PI_18 (restart point)
            ParameterValue pi18 = parsed.getParameter(ParameterIdentifier.PI_18_POINT_RELANCE);
            assertNotNull(pi18, "PI_18_POINT_RELANCE should be present");
            assertArrayEquals(new byte[] {0, 0, 0}, pi18.getValue(), "PI_18 should be all zeros");
        }

        @Test
        @DisplayName("DTF FPDU wire format correct for both channels")
        void dtfWireFormat() throws IOException {
            byte[] dtf = buildDtfFpdu();

            // Verify DTF structure
            int len = ((dtf[0] & 0xFF) << 8) | (dtf[1] & 0xFF);
            assertEquals(dtf.length, len, "DTF internal length");
            assertEquals(0x00, dtf[2] & 0xFF, "DTF phase should be 0x00");
            assertEquals(0x00, dtf[3] & 0xFF, "DTF type should be 0x00");

            // TCP round-trip
            ByteArrayOutputStream tcpBaos = new ByteArrayOutputStream();
            new TcpPesitChannel(null, new DataOutputStream(tcpBaos)).writeFrame(dtf);
            byte[] tcpResult =
                    new TcpPesitChannel(
                                    new DataInputStream(
                                            new ByteArrayInputStream(tcpBaos.toByteArray())),
                                    null)
                            .readFrame();
            assertArrayEquals(dtf, tcpResult, "TCP DTF round-trip");

            // TLS round-trip
            ByteArrayOutputStream tlsBaos = new ByteArrayOutputStream();
            new TlsPesitChannel(null, new DataOutputStream(tlsBaos)).writeFrame(dtf);
            byte[] tlsResult =
                    new TlsPesitChannel(
                                    new DataInputStream(
                                            new ByteArrayInputStream(tlsBaos.toByteArray())),
                                    null)
                            .readFrame();
            assertArrayEquals(dtf, tlsResult, "TLS DTF round-trip");
        }
    }

    @Nested
    @DisplayName("Full protocol exchange simulation")
    class ProtocolExchangeSimulation {

        @Test
        @DisplayName("TCP full exchange: CONNECT -> ACK_WRITE -> DTF")
        void tcpFullExchange() throws IOException {
            PipedOutputStream serverToClient = new PipedOutputStream();
            PipedInputStream clientReads = new PipedInputStream(serverToClient, 8192);
            PipedOutputStream clientToServer = new PipedOutputStream();
            PipedInputStream serverReads = new PipedInputStream(clientToServer, 8192);

            TcpPesitChannel clientChannel =
                    new TcpPesitChannel(
                            new DataInputStream(clientReads), new DataOutputStream(clientToServer));
            TcpPesitChannel serverChannel =
                    new TcpPesitChannel(
                            new DataInputStream(serverReads), new DataOutputStream(serverToClient));

            runWriteAckWriteExchange(clientChannel, serverChannel, "TCP");
        }

        @Test
        @DisplayName("TLS full exchange: CONNECT -> ACK_WRITE -> DTF")
        void tlsFullExchange() throws IOException {
            PipedOutputStream serverToClient = new PipedOutputStream();
            PipedInputStream clientReads = new PipedInputStream(serverToClient, 8192);
            PipedOutputStream clientToServer = new PipedOutputStream();
            PipedInputStream serverReads = new PipedInputStream(clientToServer, 8192);

            TlsPesitChannel clientChannel =
                    new TlsPesitChannel(
                            new DataInputStream(clientReads), new DataOutputStream(clientToServer));
            TlsPesitChannel serverChannel =
                    new TlsPesitChannel(
                            new DataInputStream(serverReads), new DataOutputStream(serverToClient));

            runWriteAckWriteExchange(clientChannel, serverChannel, "TLS");
        }

        /** Simulate WRITE -> ACK_WRITE exchange. Returns the ACK_WRITE received by the client. */
        private void runWriteAckWriteExchange(
                PesitChannel client, PesitChannel server, String label) throws IOException {
            // Client sends WRITE
            byte[] write = buildWriteFpdu();
            client.writeFrame(write);

            // Server reads WRITE
            byte[] serverReceived = server.readFrame();
            Fpdu writeFpdu = new FpduParser(serverReceived).parse();
            assertEquals(
                    FpduType.WRITE, writeFpdu.getFpduType(), label + ": server should parse WRITE");

            // Server builds and sends ACK_WRITE
            Fpdu ackWriteObj =
                    new Fpdu(FpduType.ACK_WRITE)
                            .withIdDst(writeFpdu.getIdSrc())
                            .withIdSrc(0)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_02_DIAG, new byte[] {0, 0, 0}))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_18_POINT_RELANCE,
                                            new byte[] {0, 0, 0}));
            byte[] ackWriteBytes = FpduBuilder.buildFpdu(ackWriteObj);
            server.writeFrame(ackWriteBytes);

            // Client reads ACK_WRITE
            byte[] clientReceived = client.readFrame();
            Fpdu ackWrite = new FpduParser(clientReceived).parse();
            assertEquals(
                    FpduType.ACK_WRITE,
                    ackWrite.getFpduType(),
                    label + ": client should parse ACK_WRITE");

            System.out.println(label + " ACK_WRITE received by client: " + hex(clientReceived));
        }
    }

    @Nested
    @DisplayName("WRITE -> ACK_WRITE wire bytes comparison (the critical test)")
    class WriteAckWriteComparison {

        @Test
        @DisplayName("ACK_WRITE wire bytes are identical between TCP and TLS channel writeFrame")
        void ackWriteWireBytesIdentical() throws IOException {
            // Build the ACK_WRITE exactly as the server does
            Fpdu ackWriteObj =
                    new Fpdu(FpduType.ACK_WRITE)
                            .withIdDst(98)
                            .withIdSrc(0)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_02_DIAG, new byte[] {0, 0, 0}))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_18_POINT_RELANCE,
                                            new byte[] {0, 0, 0}));
            byte[] fpduBytes = FpduBuilder.buildFpdu(ackWriteObj);

            // Write via TCP channel
            ByteArrayOutputStream tcpBaos = new ByteArrayOutputStream();
            new TcpPesitChannel(null, new DataOutputStream(tcpBaos)).writeFrame(fpduBytes);
            byte[] tcpWire = tcpBaos.toByteArray();

            // Write via TLS channel
            ByteArrayOutputStream tlsBaos = new ByteArrayOutputStream();
            new TlsPesitChannel(null, new DataOutputStream(tlsBaos)).writeFrame(fpduBytes);
            byte[] tlsWire = tlsBaos.toByteArray();

            System.out.println("FPDU bytes:     " + hex(fpduBytes));
            System.out.println("TCP wire bytes:  " + hex(tcpWire));
            System.out.println("TLS wire bytes:  " + hex(tlsWire));
            System.out.println("TCP wire length: " + tcpWire.length);
            System.out.println("TLS wire length: " + tlsWire.length);

            // TCP = [ext_len(2)] + FPDU, TLS = FPDU
            assertEquals(
                    fpduBytes.length + 2, tcpWire.length, "TCP = FPDU + 2-byte external prefix");
            assertEquals(fpduBytes.length, tlsWire.length, "TLS = FPDU (no external prefix)");

            // The FPDU portion must be byte-for-byte identical
            byte[] tcpFpduPortion = Arrays.copyOfRange(tcpWire, 2, tcpWire.length);
            assertArrayEquals(
                    fpduBytes, tcpFpduPortion, "TCP FPDU portion should match builder output");
            assertArrayEquals(fpduBytes, tlsWire, "TLS wire should match builder output exactly");
            assertArrayEquals(
                    tcpFpduPortion, tlsWire, "FPDU content must be identical between TCP and TLS");
        }

        @Test
        @DisplayName("ACK_WRITE read from TCP matches read from TLS")
        void ackWriteReadMatchesBetweenChannels() throws IOException {
            byte[] fpduBytes = buildAckWriteFpdu();

            // TCP write + read round-trip
            ByteArrayOutputStream tcpBaos = new ByteArrayOutputStream();
            new TcpPesitChannel(null, new DataOutputStream(tcpBaos)).writeFrame(fpduBytes);
            byte[] tcpRead =
                    new TcpPesitChannel(
                                    new DataInputStream(
                                            new ByteArrayInputStream(tcpBaos.toByteArray())),
                                    null)
                            .readFrame();

            // TLS write + read round-trip
            ByteArrayOutputStream tlsBaos = new ByteArrayOutputStream();
            new TlsPesitChannel(null, new DataOutputStream(tlsBaos)).writeFrame(fpduBytes);
            byte[] tlsRead =
                    new TlsPesitChannel(
                                    new DataInputStream(
                                            new ByteArrayInputStream(tlsBaos.toByteArray())),
                                    null)
                            .readFrame();

            System.out.println("TCP read result: " + hex(tcpRead));
            System.out.println("TLS read result: " + hex(tlsRead));

            assertArrayEquals(
                    tcpRead, tlsRead, "ACK_WRITE read from both channels must be identical");
            assertArrayEquals(fpduBytes, tcpRead, "Read result must match original FPDU bytes");
        }

        @Test
        @DisplayName("Full WRITE->ACK_WRITE exchange via piped streams - TCP")
        void fullWriteAckWriteExchangeTcp() throws IOException {
            PipedOutputStream serverToClient = new PipedOutputStream();
            PipedInputStream clientReads = new PipedInputStream(serverToClient, 8192);
            PipedOutputStream clientToServer = new PipedOutputStream();
            PipedInputStream serverReads = new PipedInputStream(clientToServer, 8192);

            TcpPesitChannel clientChannel =
                    new TcpPesitChannel(
                            new DataInputStream(clientReads), new DataOutputStream(clientToServer));
            TcpPesitChannel serverChannel =
                    new TcpPesitChannel(
                            new DataInputStream(serverReads), new DataOutputStream(serverToClient));

            byte[] ackWriteReceived = doWriteAckWriteExchange(clientChannel, serverChannel, "TCP");

            // Verify the received ACK_WRITE is correct
            Fpdu parsed = new FpduParser(ackWriteReceived).parse();
            assertEquals(FpduType.ACK_WRITE, parsed.getFpduType());
            assertEquals(0, parsed.getIdDst()); // WRITE idSrc=0 -> ACK_WRITE idDst=0
            assertNotNull(parsed.getParameter(ParameterIdentifier.PI_02_DIAG));
            assertNotNull(parsed.getParameter(ParameterIdentifier.PI_18_POINT_RELANCE));
        }

        @Test
        @DisplayName("Full WRITE->ACK_WRITE exchange via piped streams - TLS")
        void fullWriteAckWriteExchangeTls() throws IOException {
            PipedOutputStream serverToClient = new PipedOutputStream();
            PipedInputStream clientReads = new PipedInputStream(serverToClient, 8192);
            PipedOutputStream clientToServer = new PipedOutputStream();
            PipedInputStream serverReads = new PipedInputStream(clientToServer, 8192);

            TlsPesitChannel clientChannel =
                    new TlsPesitChannel(
                            new DataInputStream(clientReads), new DataOutputStream(clientToServer));
            TlsPesitChannel serverChannel =
                    new TlsPesitChannel(
                            new DataInputStream(serverReads), new DataOutputStream(serverToClient));

            byte[] ackWriteReceived = doWriteAckWriteExchange(clientChannel, serverChannel, "TLS");

            // Verify the received ACK_WRITE is correct
            Fpdu parsed = new FpduParser(ackWriteReceived).parse();
            assertEquals(FpduType.ACK_WRITE, parsed.getFpduType());
            assertEquals(0, parsed.getIdDst());
            assertNotNull(parsed.getParameter(ParameterIdentifier.PI_02_DIAG));
            assertNotNull(parsed.getParameter(ParameterIdentifier.PI_18_POINT_RELANCE));
        }

        @Test
        @DisplayName("ACK_WRITE received by client is byte-identical between TCP and TLS")
        void ackWriteReceivedByClientIdenticalBetweenTcpAndTls() throws IOException {
            // TCP exchange
            PipedOutputStream tcpS2C = new PipedOutputStream();
            PipedInputStream tcpCR = new PipedInputStream(tcpS2C, 8192);
            PipedOutputStream tcpC2S = new PipedOutputStream();
            PipedInputStream tcpSR = new PipedInputStream(tcpC2S, 8192);
            byte[] tcpAckWrite =
                    doWriteAckWriteExchange(
                            new TcpPesitChannel(
                                    new DataInputStream(tcpCR), new DataOutputStream(tcpC2S)),
                            new TcpPesitChannel(
                                    new DataInputStream(tcpSR), new DataOutputStream(tcpS2C)),
                            "TCP");

            // TLS exchange
            PipedOutputStream tlsS2C = new PipedOutputStream();
            PipedInputStream tlsCR = new PipedInputStream(tlsS2C, 8192);
            PipedOutputStream tlsC2S = new PipedOutputStream();
            PipedInputStream tlsSR = new PipedInputStream(tlsC2S, 8192);
            byte[] tlsAckWrite =
                    doWriteAckWriteExchange(
                            new TlsPesitChannel(
                                    new DataInputStream(tlsCR), new DataOutputStream(tlsC2S)),
                            new TlsPesitChannel(
                                    new DataInputStream(tlsSR), new DataOutputStream(tlsS2C)),
                            "TLS");

            System.out.println("TCP ACK_WRITE received by client: " + hex(tcpAckWrite));
            System.out.println("TLS ACK_WRITE received by client: " + hex(tlsAckWrite));

            assertArrayEquals(
                    tcpAckWrite,
                    tlsAckWrite,
                    "ACK_WRITE received by client must be byte-identical between TCP and TLS");
        }

        /**
         * Simulate WRITE -> ACK_WRITE exchange and return the ACK_WRITE bytes as received by the
         * client.
         */
        private byte[] doWriteAckWriteExchange(
                PesitChannel client, PesitChannel server, String label) throws IOException {
            // Client sends WRITE
            byte[] writeBytes = FpduBuilder.buildFpdu(FpduType.WRITE, 1, 0);
            client.writeFrame(writeBytes);

            // Server reads WRITE
            byte[] serverRecv = server.readFrame();
            Fpdu writeFpdu = new FpduParser(serverRecv).parse();
            assertEquals(
                    FpduType.WRITE, writeFpdu.getFpduType(), label + ": WRITE parsed correctly");

            // Server builds and sends ACK_WRITE (matching real server logic)
            Fpdu ackWriteObj =
                    new Fpdu(FpduType.ACK_WRITE)
                            .withIdDst(writeFpdu.getIdSrc())
                            .withIdSrc(0)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_02_DIAG, new byte[] {0, 0, 0}))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_18_POINT_RELANCE,
                                            new byte[] {0, 0, 0}));
            byte[] ackWriteBytes = FpduBuilder.buildFpdu(ackWriteObj);
            server.writeFrame(ackWriteBytes);

            System.out.println(label + " server sent ACK_WRITE: " + hex(ackWriteBytes));

            // Client reads ACK_WRITE
            byte[] clientRecv = client.readFrame();

            System.out.println(label + " client received ACK_WRITE: " + hex(clientRecv));

            return clientRecv;
        }
    }

    @Nested
    @DisplayName("Full protocol simulation (CONNECT through ACK_WRITE)")
    class FullProtocolSimulation {

        @Test
        @DisplayName("TCP: full exchange CONNECT -> ACONNECT -> WRITE -> ACK_WRITE")
        void tcpFullSequence() throws IOException {
            PipedOutputStream serverToClient = new PipedOutputStream();
            PipedInputStream clientReads = new PipedInputStream(serverToClient, 8192);
            PipedOutputStream clientToServer = new PipedOutputStream();
            PipedInputStream serverReads = new PipedInputStream(clientToServer, 8192);

            TcpPesitChannel clientChannel =
                    new TcpPesitChannel(
                            new DataInputStream(clientReads), new DataOutputStream(clientToServer));
            TcpPesitChannel serverChannel =
                    new TcpPesitChannel(
                            new DataInputStream(serverReads), new DataOutputStream(serverToClient));

            byte[] ackWrite = runFullSequence(clientChannel, serverChannel, "TCP");
            verifyAckWrite(ackWrite, "TCP");
        }

        @Test
        @DisplayName("TLS: full exchange CONNECT -> ACONNECT -> WRITE -> ACK_WRITE")
        void tlsFullSequence() throws IOException {
            PipedOutputStream serverToClient = new PipedOutputStream();
            PipedInputStream clientReads = new PipedInputStream(serverToClient, 8192);
            PipedOutputStream clientToServer = new PipedOutputStream();
            PipedInputStream serverReads = new PipedInputStream(clientToServer, 8192);

            TlsPesitChannel clientChannel =
                    new TlsPesitChannel(
                            new DataInputStream(clientReads), new DataOutputStream(clientToServer));
            TlsPesitChannel serverChannel =
                    new TlsPesitChannel(
                            new DataInputStream(serverReads), new DataOutputStream(serverToClient));

            byte[] ackWrite = runFullSequence(clientChannel, serverChannel, "TLS");
            verifyAckWrite(ackWrite, "TLS");
        }

        @Test
        @DisplayName("TCP vs TLS: full sequence produces identical ACK_WRITE")
        void tcpVsTlsFullSequenceIdentical() throws IOException {
            // TCP
            PipedOutputStream tcpS2C = new PipedOutputStream();
            PipedOutputStream tcpC2S = new PipedOutputStream();
            byte[] tcpAckWrite =
                    runFullSequence(
                            new TcpPesitChannel(
                                    new DataInputStream(new PipedInputStream(tcpS2C, 8192)),
                                    new DataOutputStream(tcpC2S)),
                            new TcpPesitChannel(
                                    new DataInputStream(new PipedInputStream(tcpC2S, 8192)),
                                    new DataOutputStream(tcpS2C)),
                            "TCP");

            // TLS
            PipedOutputStream tlsS2C = new PipedOutputStream();
            PipedOutputStream tlsC2S = new PipedOutputStream();
            byte[] tlsAckWrite =
                    runFullSequence(
                            new TlsPesitChannel(
                                    new DataInputStream(new PipedInputStream(tlsS2C, 8192)),
                                    new DataOutputStream(tlsC2S)),
                            new TlsPesitChannel(
                                    new DataInputStream(new PipedInputStream(tlsC2S, 8192)),
                                    new DataOutputStream(tlsS2C)),
                            "TLS");

            System.out.println("=== FINAL COMPARISON ===");
            System.out.println("TCP ACK_WRITE: " + hex(tcpAckWrite));
            System.out.println("TLS ACK_WRITE: " + hex(tlsAckWrite));

            assertArrayEquals(
                    tcpAckWrite,
                    tlsAckWrite,
                    "Full protocol sequence: ACK_WRITE must be identical between TCP and TLS");
        }

        private byte[] runFullSequence(PesitChannel client, PesitChannel server, String label)
                throws IOException {
            FpduReader clientReader = new FpduReader(client);
            FpduReader serverReader = new FpduReader(server);

            // 1. CONNECT
            byte[] connect =
                    FpduBuilder.buildFpdu(
                            FpduType.CONNECT,
                            0,
                            99,
                            new ParameterValue(ParameterIdentifier.PI_03_DEMANDEUR, "CLNT"),
                            new ParameterValue(ParameterIdentifier.PI_04_SERVEUR, "SRVR"),
                            new ParameterValue(ParameterIdentifier.PI_06_VERSION, 2),
                            new ParameterValue(ParameterIdentifier.PI_22_TYPE_ACCES, 2));
            client.writeFrame(connect);

            byte[] srvRecv = server.readFrame();
            Fpdu connectFpdu = new FpduParser(srvRecv).parse();
            assertEquals(FpduType.CONNECT, connectFpdu.getFpduType(), label + " CONNECT");

            // 2. ACONNECT
            byte[] aconnect =
                    FpduBuilder.buildFpdu(
                            FpduType.ACONNECT,
                            99,
                            200,
                            new ParameterValue(ParameterIdentifier.PI_06_VERSION, 2));
            server.writeFrame(aconnect);

            byte[] cltRecv = client.readFrame();
            Fpdu aconnectFpdu = new FpduParser(cltRecv).parse();
            assertEquals(FpduType.ACONNECT, aconnectFpdu.getFpduType(), label + " ACONNECT");

            // 3. WRITE
            byte[] write = FpduBuilder.buildFpdu(FpduType.WRITE, 200, 0);
            client.writeFrame(write);

            byte[] srvRecvWrite = server.readFrame();
            Fpdu writeFpdu = new FpduParser(srvRecvWrite).parse();
            assertEquals(FpduType.WRITE, writeFpdu.getFpduType(), label + " WRITE");

            // 4. ACK_WRITE (built exactly as server does)
            Fpdu ackWriteObj =
                    new Fpdu(FpduType.ACK_WRITE)
                            .withIdDst(writeFpdu.getIdSrc())
                            .withIdSrc(0)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_02_DIAG, new byte[] {0, 0, 0}))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_18_POINT_RELANCE,
                                            new byte[] {0, 0, 0}));
            byte[] ackWriteBytes = FpduBuilder.buildFpdu(ackWriteObj);
            server.writeFrame(ackWriteBytes);

            // Client reads ACK_WRITE
            byte[] ackWriteReceived = client.readFrame();

            System.out.println(label + " client received ACK_WRITE: " + hex(ackWriteReceived));
            return ackWriteReceived;
        }

        private void verifyAckWrite(byte[] ackWrite, String label) {
            Fpdu parsed = new FpduParser(ackWrite).parse();
            assertEquals(FpduType.ACK_WRITE, parsed.getFpduType(), label + " type");
            assertEquals(0, parsed.getIdDst(), label + " idDst");
            assertEquals(0, parsed.getIdSrc(), label + " idSrc");
            assertNotNull(parsed.getParameter(ParameterIdentifier.PI_02_DIAG), label + " PI_02");
            assertNotNull(
                    parsed.getParameter(ParameterIdentifier.PI_18_POINT_RELANCE), label + " PI_18");
        }
    }

    @Nested
    @DisplayName("writeFpduWithData")
    class WriteFpduWithData {

        @Test
        @DisplayName("TCP writeFpduWithData round-trip")
        void tcpWriteFpduWithDataRoundTrip() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            TcpPesitChannel writeChannel = new TcpPesitChannel(null, out);

            byte[] payload = "test data".getBytes();
            writeChannel.writeFpduWithData(FpduType.DTF, 1, 1, payload);

            DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
            TcpPesitChannel readChannel = new TcpPesitChannel(in, null);
            byte[] result = readChannel.readFrame();

            // Verify it's a valid DTF
            Fpdu fpdu = new FpduParser(result).parse();
            assertEquals(FpduType.DTF, fpdu.getFpduType());
            assertArrayEquals(payload, fpdu.getData());
        }

        @Test
        @DisplayName("TLS writeFpduWithData round-trip")
        void tlsWriteFpduWithDataRoundTrip() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            TlsPesitChannel writeChannel = new TlsPesitChannel(null, out);

            byte[] payload = "test data".getBytes();
            writeChannel.writeFpduWithData(FpduType.DTF, 1, 1, payload);

            DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
            TlsPesitChannel readChannel = new TlsPesitChannel(in, null);
            byte[] result = readChannel.readFrame();

            // Verify it's a valid DTF
            Fpdu fpdu = new FpduParser(result).parse();
            assertEquals(FpduType.DTF, fpdu.getFpduType());
            assertArrayEquals(payload, fpdu.getData());
        }

        @Test
        @DisplayName("Both channels produce same FPDU bytes from writeFpduWithData")
        void bothChannelsSameOutput() throws IOException {
            byte[] payload = "test data".getBytes();

            // TCP
            ByteArrayOutputStream tcpBaos = new ByteArrayOutputStream();
            new TcpPesitChannel(null, new DataOutputStream(tcpBaos))
                    .writeFpduWithData(FpduType.DTF, 1, 1, payload);
            byte[] tcpWire = tcpBaos.toByteArray();

            // TLS
            ByteArrayOutputStream tlsBaos = new ByteArrayOutputStream();
            new TlsPesitChannel(null, new DataOutputStream(tlsBaos))
                    .writeFpduWithData(FpduType.DTF, 1, 1, payload);
            byte[] tlsWire = tlsBaos.toByteArray();

            // TCP = ext_prefix(2) + FPDU, TLS = FPDU
            assertEquals(tlsWire.length + 2, tcpWire.length);
            assertArrayEquals(
                    tlsWire,
                    Arrays.copyOfRange(tcpWire, 2, tcpWire.length),
                    "FPDU portion should be identical");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Minimum valid FPDU (6 bytes header only)")
        void minimumFpdu() throws IOException {
            // Minimum FPDU: just the 6-byte header
            byte[] minFpdu = FpduBuilder.buildFpdu(FpduType.WRITE, 1, 0);
            assertEquals(6, minFpdu.length);

            // TCP round-trip
            ByteArrayOutputStream tcpBaos = new ByteArrayOutputStream();
            new TcpPesitChannel(null, new DataOutputStream(tcpBaos)).writeFrame(minFpdu);
            byte[] tcpResult =
                    new TcpPesitChannel(
                                    new DataInputStream(
                                            new ByteArrayInputStream(tcpBaos.toByteArray())),
                                    null)
                            .readFrame();
            assertArrayEquals(minFpdu, tcpResult);

            // TLS round-trip
            ByteArrayOutputStream tlsBaos = new ByteArrayOutputStream();
            new TlsPesitChannel(null, new DataOutputStream(tlsBaos)).writeFrame(minFpdu);
            byte[] tlsResult =
                    new TlsPesitChannel(
                                    new DataInputStream(
                                            new ByteArrayInputStream(tlsBaos.toByteArray())),
                                    null)
                            .readFrame();
            assertArrayEquals(minFpdu, tlsResult);
        }

        @Test
        @DisplayName("Multiple FPDUs written sequentially")
        void multipleSequentialFpdus() throws IOException {
            byte[] fpdu1 = buildConnectFpdu();
            byte[] fpdu2 = buildAckWriteFpdu();
            byte[] fpdu3 = buildDtfFpdu();

            // Write 3 FPDUs via TLS
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            TlsPesitChannel writeChannel = new TlsPesitChannel(null, out);
            writeChannel.writeFrame(fpdu1);
            writeChannel.writeFrame(fpdu2);
            writeChannel.writeFrame(fpdu3);

            // Read them back
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
            TlsPesitChannel readChannel = new TlsPesitChannel(in, null);

            assertArrayEquals(fpdu1, readChannel.readFrame(), "First FPDU");
            assertArrayEquals(fpdu2, readChannel.readFrame(), "Second FPDU");
            assertArrayEquals(fpdu3, readChannel.readFrame(), "Third FPDU");
        }

        @Test
        @DisplayName("Pre-connection ACK0 (non-FPDU) via writeFrame")
        void preConnectionAck0() throws IOException {
            // Pre-connection ACK0 is a raw 4-byte message, NOT an FPDU
            byte[] ack0 =
                    new byte[] {
                        (byte) 0xC1, (byte) 0xC3, (byte) 0xD2, (byte) 0xF0
                    }; // EBCDIC "ACK0"

            // TCP: writeFrame adds length prefix
            ByteArrayOutputStream tcpBaos = new ByteArrayOutputStream();
            new TcpPesitChannel(null, new DataOutputStream(tcpBaos)).writeFrame(ack0);
            byte[] tcpWire = tcpBaos.toByteArray();
            assertEquals(6, tcpWire.length, "TCP: 2-byte prefix + 4-byte ACK0");
            assertEquals(0x00, tcpWire[0]);
            assertEquals(0x04, tcpWire[1]);

            // TLS: writeFrame writes raw bytes
            ByteArrayOutputStream tlsBaos = new ByteArrayOutputStream();
            new TlsPesitChannel(null, new DataOutputStream(tlsBaos)).writeFrame(ack0);
            byte[] tlsWire = tlsBaos.toByteArray();
            assertEquals(4, tlsWire.length, "TLS: raw 4-byte ACK0");
            assertArrayEquals(ack0, tlsWire);
        }

        @Test
        @DisplayName("injectRawData into FpduReader works for both channel types")
        void injectRawDataBothChannels() throws IOException {
            byte[] fpdu = buildConnectFpdu();

            // TCP: inject, then verify FpduReader can parse
            TcpPesitChannel tcpChannel =
                    new TcpPesitChannel(
                            new DataInputStream(new ByteArrayInputStream(new byte[0])), null);
            FpduReader tcpReader = new FpduReader(tcpChannel);
            tcpReader.injectRawData(fpdu);
            assertTrue(tcpReader.hasPending());
            Fpdu tcpFpdu = tcpReader.read();
            assertEquals(FpduType.CONNECT, tcpFpdu.getFpduType());

            // TLS: inject, then verify FpduReader can parse
            TlsPesitChannel tlsChannel =
                    new TlsPesitChannel(
                            new DataInputStream(new ByteArrayInputStream(new byte[0])), null);
            FpduReader tlsReader = new FpduReader(tlsChannel);
            tlsReader.injectRawData(fpdu);
            assertTrue(tlsReader.hasPending());
            Fpdu tlsFpdu = tlsReader.read();
            assertEquals(FpduType.CONNECT, tlsFpdu.getFpduType());
        }
    }

    @Nested
    @DisplayName("FpduIO TCP method consistency")
    class FpduIoConsistency {

        @Test
        @DisplayName("FpduIO.readRawFpdu returns data WITH internal length prefix")
        void readRawFpduIncludesInternalLength() throws IOException {
            byte[] fpdu = buildAckWriteFpdu();

            // Simulate TCP wire: [ext_len(2)][fpdu_with_internal_len]
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream writer = new DataOutputStream(baos);
            writer.writeShort(fpdu.length);
            writer.write(fpdu);

            DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
            byte[] result = FpduIO.readRawFpdu(in);

            assertArrayEquals(
                    fpdu,
                    result,
                    "FpduIO.readRawFpdu should return FPDU including internal length");

            // First 2 bytes should be the internal length
            int internalLen = ((result[0] & 0xFF) << 8) | (result[1] & 0xFF);
            assertEquals(
                    result.length,
                    internalLen,
                    "Internal length field should equal total FPDU size");
        }

        @Test
        @DisplayName("FpduIO.writeRawFpdu writes [ext_len(2)][fpdu_data]")
        void writeRawFpduAddsExternalPrefix() throws IOException {
            byte[] fpdu = buildAckWriteFpdu();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            FpduIO.writeRawFpdu(out, fpdu);

            byte[] wire = baos.toByteArray();
            assertEquals(fpdu.length + 2, wire.length);

            int extLen = ((wire[0] & 0xFF) << 8) | (wire[1] & 0xFF);
            assertEquals(fpdu.length, extLen, "External prefix = FPDU length");

            byte[] fpduPortion = Arrays.copyOfRange(wire, 2, wire.length);
            assertArrayEquals(fpdu, fpduPortion, "FPDU data after external prefix");
        }
    }
}
