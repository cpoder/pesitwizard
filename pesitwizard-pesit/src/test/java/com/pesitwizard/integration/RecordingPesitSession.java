package com.pesitwizard.integration;

import java.io.IOException;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.integration.PesitSessionRecorder.Direction;
import com.pesitwizard.session.PesitSession;
import com.pesitwizard.transport.TransportChannel;

/**
 * PesitSession wrapper that records all FPDU exchanges.
 */
public class RecordingPesitSession extends PesitSession {

    private final PesitSessionRecorder recorder;

    public RecordingPesitSession(TransportChannel channel, PesitSessionRecorder recorder) throws IOException {
        super(channel);
        this.recorder = recorder;
    }

    @Override
    public void sendFpdu(Fpdu fpdu) throws IOException {
        recorder.record(Direction.SENT, fpdu);
        super.sendFpdu(fpdu);
    }

    @Override
    public Fpdu receiveFpdu() throws IOException {
        Fpdu fpdu = super.receiveFpdu();
        recorder.record(Direction.RECEIVED, fpdu);
        return fpdu;
    }

    @Override
    public Fpdu sendFpduWithAck(Fpdu fpdu) throws IOException, InterruptedException {
        recorder.record(Direction.SENT, fpdu);
        Fpdu ack = super.sendFpduWithAck(fpdu);
        // Note: ack is already recorded by parent via receiveFpdu, but parent doesn't
        // call our override
        // So we record here - but parent uses checkForAbort which doesn't call
        // receiveFpdu()
        recorder.record(Direction.RECEIVED, ack);
        return ack;
    }
}
