package com.pesitwizard.fpdu;

import java.nio.ByteBuffer;

import lombok.extern.slf4j.Slf4j;

/**
 * Parser for a single PeSIT FPDU.
 * FPDU structure: [size (2 bytes, binary)][phase][type][idDst][idSrc][params or
 * data]
 *
 * Note on IBM CX encoding:
 * - Pre-connection message (24 bytes): PURE EBCDIC
 * - FPDU messages: ALL fields are in ASCII/binary (no EBCDIC conversion needed)
 *
 * Note: The global frame length has been consumed by FpduIO.readRawFpdu(),
 * but each FPDU inside the frame has its own 2-byte length prefix.
 */
@Slf4j
public class FpduParser {
    private final ByteBuffer buffer;
    private final int fpduLength;

    /**
     * Create parser from ByteBuffer positioned at start of FPDU (including length
     * prefix).
     * This is the preferred constructor. The buffer position will be advanced by
     * fpduLength bytes after parsing.
     */
    public FpduParser(ByteBuffer buffer) {
        this.fpduLength = buffer.getShort() & 0xFFFF;
        // Create a slice limited to this FPDU's data (fpduLength - 2 bytes already
        // read)
        int dataLen = fpduLength - 2;
        ByteBuffer slice = buffer.slice();
        slice.limit(dataLen);
        this.buffer = slice;
        // Advance original buffer position past this FPDU
        buffer.position(buffer.position() + dataLen);
    }

    /**
     * Create parser from byte array containing complete FPDU (with length prefix).
     */
    public FpduParser(byte[] data) {
        this(ByteBuffer.wrap(data));
        if (fpduLength != data.length) {
            log.warn("FPDU length mismatch: FPDU header says {}, actual data is {} bytes", fpduLength, data.length);
        }
    }

    @Deprecated
    public FpduParser(byte[] data, boolean ebcdicEncoding) {
        this(data); // Ignore ebcdicEncoding flag - FPDU parameters are ASCII
    }

    public Fpdu parse() {
        Fpdu fpdu = new Fpdu();
        int phase = buffer.get() & 0xFF;
        int type = buffer.get() & 0xFF;
        fpdu.setFpduType(FpduType.from(phase, type));
        log.debug("Parsing FPDU: phase={}, type={} -> {}", phase, type, fpdu.getFpduType());
        int idDest = buffer.get();
        int idSrc = buffer.get();
        fpdu.setIdDst(idDest);
        fpdu.setIdSrc(idSrc);

        // DTF FPDUs contain raw data, not parameters
        // Data length = fpduLength - 6 (2 len + 1 phase + 1 type + 1 idDst + 1 idSrc)
        if (fpdu.getFpduType() == FpduType.DTF || fpdu.getFpduType() == FpduType.DTFDA
                || fpdu.getFpduType() == FpduType.DTFMA || fpdu.getFpduType() == FpduType.DTFFA) {
            int dataLen = buffer.remaining(); // Already limited by slice
            if (dataLen > 0) {
                byte[] rawData = new byte[dataLen];
                buffer.get(rawData);
                fpdu.setData(rawData);
                log.info("{} FPDU contains {} bytes of data", fpdu.getFpduType(), dataLen);
            }
            return fpdu;
        }

        while (buffer.hasRemaining()) {
            int paramId = buffer.get() & 0xFF;
            if (!buffer.hasRemaining()) {
                log.warn("Incomplete parameter: ID={} without length byte", paramId);
                break;
            }
            int paramLength = buffer.get() & 0xFF;
            if (paramLength == 0xFF) {
                if (!buffer.hasRemaining()) {
                    log.warn("Incomplete parameter: ID={} with length byte 0xFF without short length", paramId);
                    break;
                }
                paramLength = buffer.getShort() & 0xFFFF;
            }
            byte[] paramData = new byte[paramLength];
            if (paramLength > 0) {
                if (paramLength > buffer.remaining()) {
                    log.warn("Incomplete parameter: ID={} with length {} exceeds remaining buffer size {}", paramId,
                            paramLength, buffer.remaining());
                    break;
                }
                buffer.get(paramData);
            }
            if (ParameterIdentifier.fromId(paramId) != null) {
                ParameterIdentifier paramIdEnum = ParameterIdentifier.fromId(paramId);
                ParameterValue paramValue = new ParameterValue(paramIdEnum, paramData);
                log.info("PI {} found which is {} and has a size of {} bytes with value {}", paramId, paramIdEnum,
                        paramLength, paramValue.toString());
                fpdu.getParameters().add(paramValue);
            } else if (ParameterGroupIdentifier.fromId(paramId) != null) {
                ParameterGroupIdentifier groupId = ParameterGroupIdentifier.fromId(paramId);
                log.info("PGI {} found which is {}", paramId, groupId);
                ParameterValue groupParameterValue = new ParameterValue(groupId, new ParameterValue[0]);
                fpdu.getParameters().add(groupParameterValue);
                ByteBuffer groupBuffer = ByteBuffer.wrap(paramData);
                while (groupBuffer.hasRemaining()) {
                    int groupParamId = groupBuffer.get() & 0xFF;
                    int groupParamLength = groupBuffer.get() & 0xFF;
                    byte[] groupParamData = new byte[groupParamLength];
                    groupBuffer.get(groupParamData);
                    ParameterIdentifier groupParamIdEnum = ParameterIdentifier.fromId(groupParamId);
                    if (groupParamIdEnum != null) {
                        ParameterValue groupParamValue = new ParameterValue(groupParamIdEnum, groupParamData);
                        log.info("PI {} found which is {} and has a size of {} bytes with value {}", groupParamId,
                                groupParamIdEnum, groupParamLength, groupParamValue.toString());
                        groupParameterValue.getValues().add(groupParamValue);
                    } else {
                        throw new UnknownParameterException(groupParamId, groupParamLength, "PGI " + groupId.name());
                    }
                }
            } else {
                throw new UnknownParameterException(paramId, paramLength, "FPDU " + fpdu.getFpduType().name());
            }
        }
        return fpdu;
    }
}
