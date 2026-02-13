package com.pesitwizard.channel;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduBuilder;
import com.pesitwizard.fpdu.FpduIO;
import com.pesitwizard.fpdu.FpduType;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * TCP PeSIT channel: each FPDU is prefixed with a 2-byte external length.
 *
 * <p>Wire format: [ext_len(2)][fpdu_bytes...] where fpdu_bytes already contain the FPDU's own
 * internal length field.
 */
public class TcpPesitChannel implements PesitChannel {

    private final DataInputStream in;
    private final DataOutputStream out;

    public TcpPesitChannel(DataInputStream in, DataOutputStream out) {
        this.in = in;
        this.out = out;
    }

    @Override
    public byte[] readFrame() throws IOException {
        return FpduIO.readRawFpdu(in);
    }

    @Override
    public byte[] readFrame(int maxLength) throws IOException {
        return FpduIO.readRawFpdu(in, maxLength);
    }

    @Override
    public void writeFrame(byte[] data) throws IOException {
        FpduIO.writeRawFpdu(out, data);
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
        return false;
    }
}
