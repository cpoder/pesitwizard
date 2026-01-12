package com.pesitwizard.client.pesit;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.session.PesitSession;

/**
 * Wrapper around the shared FpduReader for client-side usage with PesitSession.
 * This provides backward compatibility while using the shared concatenated FPDU handling.
 */
public class FpduReader {
    private final PesitSession session;
    private com.pesitwizard.fpdu.FpduReader sharedReader;

    public FpduReader(PesitSession session) {
        this.session = session;
    }

    /**
     * Read the next FPDU from the channel.
     * Handles both single and concatenated FPDUs transparently.
     */
    public Fpdu read() throws IOException {
        // Lazy initialization: read raw data and create shared reader for each data entity
        if (sharedReader == null || !sharedReader.hasPending()) {
            byte[] rawData = session.receiveRawFpdu();
            sharedReader = new com.pesitwizard.fpdu.FpduReader(
                new DataInputStream(new ByteArrayInputStream(rawData)));
        }
        return sharedReader.read();
    }

    /**
     * Check if there are buffered FPDUs waiting to be read.
     */
    public boolean hasPending() {
        return sharedReader != null && sharedReader.hasPending();
    }
}
