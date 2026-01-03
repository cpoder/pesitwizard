package com.pesitwizard.session;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;

import java.io.IOException;

import com.pesitwizard.exception.PesitException;
import com.pesitwizard.fpdu.DiagnosticCode;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduBuilder;
import com.pesitwizard.fpdu.FpduParser;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.transport.TransportChannel;

import lombok.extern.slf4j.Slf4j;

/**
 * PESIT Session Management
 * Represents a PESIT protocol session between two partners
 */
@Slf4j
public class PesitSession implements AutoCloseable {
    private TransportChannel channel;
    private boolean strict = false;

    public PesitSession(TransportChannel channel) throws IOException {
        this.channel = channel;
        this.channel.connect();
    }

    public PesitSession(TransportChannel channel, boolean strict) throws IOException {
        this.channel = channel;
        this.channel.connect();
        this.strict = strict;
    }

    public void close() throws IOException {
        this.channel.close();
    }

    private Fpdu checkForAbort(Fpdu context) throws IOException, InterruptedException {
        // Wait a bit for server to process
        Thread.sleep(300);

        byte[] response = channel.receive();
        FpduParser parser = new FpduParser(response);
        Fpdu fpdu = parser.parse();

        // Check for ABORT (0x40 0x25) or RCONNECT (connection rejected)
        if (fpdu.getFpduType() == FpduType.ABORT || fpdu.getFpduType() == FpduType.IDT
                || fpdu.getFpduType() == FpduType.RCONNECT) {
            log.error("✗ {} received after {}!", fpdu.getFpduType(), context.getFpduType());
            if (fpdu.hasParameter(PI_02_DIAG)) {
                ParameterValue diagParam = fpdu.getParameter(PI_02_DIAG);
                log.error("PeSIT exception after {}", context.getFpduType());
                DiagnosticCode diagCode = DiagnosticCode.fromParameterValue(diagParam);
                String diagMessage = diagCode != null ? diagCode.getMessage() : formatDiagBytes(diagParam.getValue());
                log.error("Diagnostic code: {}, message: {}", formatDiagBytes(diagParam.getValue()), diagMessage);
                // Include free message if present
                if (fpdu.hasParameter(PI_99_MESSAGE_LIBRE)) {
                    byte[] msgBytes = fpdu.getParameter(PI_99_MESSAGE_LIBRE).getValue();
                    String freeMsg = msgBytes != null ? new String(msgBytes) : "";
                    log.error("Server message: {}", freeMsg);
                }
                throw new PesitException(diagParam);
            } else {
                throw new IOException("Server sent " + fpdu.getFpduType() + " without diagnostic code after "
                        + context.getFpduType());
            }
        }

        // Check diagnostic in ACK - non-zero means failure
        if (fpdu.hasParameter(PI_02_DIAG)) {
            ParameterValue diagParam = fpdu.getParameter(PI_02_DIAG);
            byte[] diagBytes = diagParam.getValue();
            // Check if diagnostic is non-zero (failure)
            if (diagBytes != null && diagBytes.length >= 3
                    && (diagBytes[0] != 0 || diagBytes[1] != 0 || diagBytes[2] != 0)) {
                DiagnosticCode diagCode = DiagnosticCode.fromParameterValue(diagParam);
                String diagMessage = diagCode != null ? diagCode.getMessage() : formatDiagBytes(diagBytes);
                log.error("Server rejected request: {} ({})", diagMessage, formatDiagBytes(diagBytes));
                throw new PesitException(diagParam);
            }
            log.info("Diagnostic: success");
        }

        if (fpdu.getFpduType() != context.getFpduType().getExpectedAck()) {
            log.error("✗ Unexpected response after {}: received {} instead of {}", context.getFpduType(),
                    fpdu.getFpduType(),
                    context.getFpduType().getExpectedAck());
            if (strict) {
                throw new IOException("Unexpected response: " + fpdu.getFpduType() + " after " + context.getFpduType());
            }
            log.warn("Continuing without strict check, but this may lead to unexpected behavior.");
        }

        log.info("Received {} with vaue {}", fpdu.getFpduType(), fpdu);

        return fpdu;
    }

    public Fpdu sendFpduWithAck(Fpdu fpdu)
            throws IOException, InterruptedException {
        sendFpdu(fpdu);
        return checkForAbort(fpdu);
    }

    public void sendFpdu(Fpdu fpdu) throws IOException {
        channel.send(FpduBuilder.buildFpdu(fpdu));
    }

    public void sendFpduWithData(Fpdu fpdu, byte[] data)
            throws IOException, InterruptedException {
        byte[] fpduBytes = FpduBuilder.buildFpdu(fpdu.getFpduType(), fpdu.getIdDst(), fpdu.getIdSrc(), data);
        channel.send(fpduBytes);
    }

    public Fpdu sendFpduWithDataAndAck(Fpdu fpdu, byte[] data)
            throws IOException, InterruptedException {
        sendFpduWithData(fpdu, data);
        return checkForAbort(fpdu);
    }

    /**
     * Receive a single FPDU from the server
     * Used for receiving DTF data chunks during file reception
     */
    public Fpdu receiveFpdu() throws IOException {
        byte[] response = channel.receive();
        FpduParser parser = new FpduParser(response);
        return parser.parse();
    }

    /**
     * Receive raw FPDU bytes from the server
     * Used for receiving DTF data where we need the raw bytes including data
     * payload
     */
    public byte[] receiveRawFpdu() throws IOException {
        return channel.receive();
    }

    /**
     * Format diagnostic bytes as hex string for unknown codes
     */
    private String formatDiagBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 3) {
            return "Unknown";
        }
        return String.format("0x%02X%02X%02X", bytes[0] & 0xFF, bytes[1] & 0xFF, bytes[2] & 0xFF);
    }
}
