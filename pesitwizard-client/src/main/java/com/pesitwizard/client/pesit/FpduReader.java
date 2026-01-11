package com.pesitwizard.client.pesit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduParser;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.session.PesitSession;

import lombok.extern.slf4j.Slf4j;

/**
 * Reads FPDUs from a PeSIT session, handling concatenated FPDUs (PeSIT section
 * 4.5).
 * 
 * A data entity received from the transport may contain multiple FPDUs.
 * This reader buffers them and returns one FPDU at a time.
 */
@Slf4j
public class FpduReader {
    private final PesitSession session;
    private final Deque<Fpdu> pendingFpdus = new ArrayDeque<>();

    public FpduReader(PesitSession session) {
        this.session = session;
    }

    /**
     * Read the next FPDU from the channel.
     * Handles both single and concatenated FPDUs transparently.
     */
    public Fpdu read() throws IOException {
        if (!pendingFpdus.isEmpty()) {
            return pendingFpdus.poll();
        }

        byte[] data = session.receiveRawFpdu();
        parseDataEntity(data);

        return pendingFpdus.poll();
    }

    /**
     * Check if there are buffered FPDUs waiting to be read.
     */
    public boolean hasPending() {
        return !pendingFpdus.isEmpty();
    }

    /**
     * Parse a data entity which may contain one or more FPDUs.
     */
    private void parseDataEntity(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        // Check if this looks like concatenated FPDUs:
        // First 2 bytes = sub-FPDU length, next 2 bytes = FPDU type ID
        if (data.length >= 6) {
            int firstLen = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
            int fpduTypeId = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);

            // Valid concatenation: length makes sense AND type ID looks valid (known FPDU
            // types are < 50)
            if (firstLen >= 6 && firstLen <= data.length && fpduTypeId > 0 && fpduTypeId < 50) {
                parseConcatenatedFpdus(buffer);
                return;
            }
        }

        // Single FPDU - no internal length prefix
        FpduParser parser = new FpduParser(data);
        pendingFpdus.add(parser.parse());
    }

    /**
     * Parse concatenated FPDUs from a buffer.
     * Structure: [len1][fpdu1_content][len2][fpdu2_content]...
     */
    private void parseConcatenatedFpdus(ByteBuffer buffer) {
        ByteArrayOutputStream dtfData = null;
        Fpdu dtfFpdu = null;

        while (buffer.remaining() >= 6) {
            int subLen = buffer.getShort() & 0xFFFF;
            if (subLen < 6 || subLen > buffer.remaining() + 2) {
                log.warn("Invalid sub-FPDU length: {} (remaining: {})", subLen, buffer.remaining());
                break;
            }

            // Read sub-FPDU content (without the length prefix we just read)
            byte[] subContent = new byte[subLen - 2];
            buffer.get(subContent);

            FpduParser parser = new FpduParser(subContent);
            Fpdu fpdu = parser.parse();

            // For DTF* FPDUs, aggregate data into a single FPDU
            if (isDtfType(fpdu.getFpduType())) {
                if (dtfFpdu == null) {
                    dtfFpdu = fpdu;
                    dtfData = new ByteArrayOutputStream();
                }
                if (fpdu.getData() != null) {
                    try {
                        dtfData.write(fpdu.getData());
                    } catch (IOException e) {
                        // ByteArrayOutputStream doesn't throw
                    }
                }
            } else {
                // Non-DTF FPDU: flush any accumulated DTF data first
                if (dtfFpdu != null) {
                    dtfFpdu.setData(dtfData.toByteArray());
                    pendingFpdus.add(dtfFpdu);
                    dtfFpdu = null;
                    dtfData = null;
                }
                pendingFpdus.add(fpdu);
            }
        }

        // Flush remaining DTF data
        if (dtfFpdu != null) {
            dtfFpdu.setData(dtfData.toByteArray());
            pendingFpdus.add(dtfFpdu);
            log.debug("Aggregated {} bytes from concatenated DTF FPDUs", dtfData.size());
        }
    }

    private boolean isDtfType(FpduType type) {
        return type == FpduType.DTF || type == FpduType.DTFDA
                || type == FpduType.DTFMA || type == FpduType.DTFFA;
    }
}
