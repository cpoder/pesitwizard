package com.pesitwizard.channel;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Abstraction for PeSIT protocol framing over different transports.
 *
 * <p>TCP uses a 2-byte external length prefix before each FPDU. TLS writes FPDUs directly (TLS
 * records provide framing).
 */
public interface PesitChannel {

    /** Read one FPDU frame (raw bytes including the FPDU's internal 2-byte length). */
    byte[] readFrame() throws IOException;

    /** Read one FPDU frame with a maximum length constraint. */
    byte[] readFrame(int maxLength) throws IOException;

    /**
     * Write raw frame bytes with proper transport framing. TCP adds the external 2-byte length
     * prefix; TLS writes directly.
     */
    void writeFrame(byte[] data) throws IOException;

    /** Build an FPDU from the given object and write it with proper framing. */
    void writeFpdu(Fpdu fpdu) throws IOException;

    /** Build a DTF FPDU and write it with proper framing. */
    void writeFpduWithData(FpduType type, int idDst, int idSrc, byte[] payload) throws IOException;

    /** Direct stream access for FpduReader and special cases. */
    DataInputStream getInputStream();

    /** Direct stream access for writing. */
    DataOutputStream getOutputStream();

    /** Whether this channel uses TLS framing (no external prefix). */
    boolean isTls();
}
