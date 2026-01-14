package com.pesitwizard.fpdu;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import lombok.extern.slf4j.Slf4j;

/**
 * Reads FPDUs from a TCP stream, handling concatenated FPDUs (PeSIT section
 * 4.5).
 *
 * A data entity received from the transport may contain multiple FPDUs.
 * This reader buffers them and returns one FPDU at a time.
 *
 * Shared between client and server implementations.
 */
@Slf4j
public class FpduReader {
    private final DataInputStream input;
    private final Deque<Fpdu> pendingFpdus = new ArrayDeque<>();

    public FpduReader(DataInputStream input) {
        this.input = input;
    }

    /**
     * Read the next FPDU from the stream.
     * Handles both single and concatenated FPDUs transparently.
     */
    public Fpdu read() throws IOException {
        // Return buffered FPDU if available
        if (!pendingFpdus.isEmpty()) {
            return pendingFpdus.poll();
        }

        // Read next data entity from stream
        byte[] data = FpduIO.readRawFpdu(input);
        parseBuffer(data);

        // Return first parsed FPDU
        return pendingFpdus.poll();
    }

    /**
     * Read raw FPDU data (for backward compatibility or special handling).
     */
    public byte[] readRaw() throws IOException {
        if (!pendingFpdus.isEmpty()) {
            throw new IllegalStateException("Cannot read raw data when FPDUs are buffered");
        }
        return FpduIO.readRawFpdu(input);
    }

    /**
     * Check if there are buffered FPDUs waiting to be read.
     */
    public boolean hasPending() {
        return !pendingFpdus.isEmpty();
    }

    /**
     * Inject raw FPDU data that was already read from the stream.
     * Useful when the first message needs special handling (e.g., pre-connection
     * handshake).
     */
    public void injectRawData(byte[] data) {
        parseBuffer(data);
    }

    /**
     * Parse buffer containing one or more FPDUs.
     * Format: [len1][fpdu1_content][len2][fpdu2_content]...
     * Each FPDU: [len 2B][phase 1B][type 1B][idDst 1B][idSrc 1B][data/params]
     */
    private void parseBuffer(byte[] data) {
        if (data.length < 6) {
            log.warn("Data too short for FPDU: {} bytes", data.length);
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        int fpduCount = 0;

        while (buffer.remaining() >= 6) {
            // Peek at length to validate
            int fpduLen = buffer.getShort(buffer.position()) & 0xFFFF;
            if (fpduLen < 6 || fpduLen > buffer.remaining()) {
                log.warn("Invalid FPDU length: {} (remaining: {})", fpduLen, buffer.remaining());
                break;
            }

            // Parse FPDU (FpduParser reads length and advances buffer position)
            FpduParser parser = new FpduParser(buffer);
            Fpdu fpdu = parser.parse();
            pendingFpdus.add(fpdu);
            fpduCount++;

            log.debug("Parsed FPDU #{}: type={}, dataLen={}", fpduCount, fpdu.getFpduType(),
                    fpdu.getData() != null ? fpdu.getData().length : 0);
        }

        log.debug("Parsed {} FPDU(s) from {} bytes", fpduCount, data.length);
    }
}
