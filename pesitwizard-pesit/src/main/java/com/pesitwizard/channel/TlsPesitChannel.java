package com.pesitwizard.channel;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduBuilder;
import com.pesitwizard.fpdu.FpduIO;
import com.pesitwizard.fpdu.FpduType;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * TLS PeSIT channel: FPDUs are written directly without external length prefix.
 *
 * <p>TLS records already provide frame boundaries, so no additional framing is needed. The FPDU's
 * own internal 2-byte length field is used for parsing.
 */
public class TlsPesitChannel implements PesitChannel {

    private final DataInputStream in;
    private final DataOutputStream out;

    public TlsPesitChannel(DataInputStream in, DataOutputStream out) {
        this.in = in;
        this.out = out;
    }

    @Override
    public byte[] readFrame() throws IOException {
        return readFrame(FpduIO.DEFAULT_MAX_FPDU_LENGTH);
    }

    @Override
    public byte[] readFrame(int maxLength) throws IOException {
        // Read the FPDU's own 2-byte length field (this IS part of the FPDU, no external prefix)
        int length = in.readUnsignedShort();
        if (length < 6) {
            throw new IOException("Invalid FPDU length: " + length + " (minimum is 6)");
        }
        if (length > maxLength) {
            throw new IOException(
                    "FPDU length "
                            + length
                            + " exceeds maximum allowed length "
                            + maxLength
                            + " bytes");
        }
        // Reconstruct the complete FPDU with its length field
        byte[] data = new byte[length];
        data[0] = (byte) ((length >> 8) & 0xFF);
        data[1] = (byte) (length & 0xFF);
        in.readFully(data, 2, length - 2);
        return data;
    }

    @Override
    public void writeFrame(byte[] data) throws IOException {
        // No external length prefix for TLS - write FPDU directly
        out.write(data);
        out.flush();
    }

    @Override
    public void writeFpdu(Fpdu fpdu) throws IOException {
        writeFrame(FpduBuilder.buildFpdu(fpdu));
    }

    @Override
    public void writeFpduWithData(FpduType type, int idDst, int idSrc, byte[] payload)
            throws IOException {
        writeFrame(FpduBuilder.buildFpdu(type, idDst, idSrc, payload));
    }

    @Override
    public DataInputStream getInputStream() {
        return in;
    }

    @Override
    public DataOutputStream getOutputStream() {
        return out;
    }

    @Override
    public boolean isTls() {
        return true;
    }
}
