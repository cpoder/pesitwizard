package com.pesitwizard.fpdu;

import java.nio.ByteBuffer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FpduParser {
    ByteBuffer buffer;

    public FpduParser(byte[] data) {
        this.buffer = ByteBuffer.wrap(data);
        int len = buffer.getShort() & 0xFFFF; // Unsigned short
        if (len != data.length) {
            throw new IllegalArgumentException("Invalid Fpdu length: expected " + len + ", got " + data.length);
        }
    }

    public Fpdu parse() {
        // Read the length of the Fpdu
        // Implement parsing logic here
        // Read the header, type, and parameters from the input stream
        // Construct and return an Fpdu object
        Fpdu fpdu = new Fpdu();
        // Example: Read the first byte as phase, second byte as type, etc.
        int phase = buffer.get() & 0xFF;
        int type = buffer.get() & 0xFF;
        System.out.println("Parsing Fpdu with phase: " + phase + ", type: " + type);
        // Set the Fpdu type based on phase and type
        fpdu.setFpduType(FpduType.from(phase, type));
        System.out.println("Fpdu type is: " + fpdu.getFpduType());
        int idDest = buffer.get();
        int idSrc = buffer.get();
        fpdu.setIdDst(idDest);
        fpdu.setIdSrc(idSrc);

        // DTF FPDUs contain raw data, not parameters
        if (fpdu.getFpduType() == FpduType.DTF || fpdu.getFpduType() == FpduType.DTFDA
                || fpdu.getFpduType() == FpduType.DTFMA || fpdu.getFpduType() == FpduType.DTFFA) {
            if (buffer.hasRemaining()) {
                byte[] rawData = new byte[buffer.remaining()];
                buffer.get(rawData);
                fpdu.setData(rawData);
                log.info("{} FPDU contains {} bytes of data", fpdu.getFpduType(), rawData.length);
            }
            return fpdu;
        }

        while (buffer.hasRemaining()) {
            // Read parameters from the buffer
            int paramId = buffer.get();
            System.out.println("Parsing parameter ID: " + paramId);
            int paramLength = buffer.get();
            if (paramLength == 0xff) {
                paramLength = buffer.getShort();
            }
            byte[] paramData = new byte[paramLength];
            buffer.get(paramData);
            if (ParameterIdentifier.fromId(paramId) != null) {
                ParameterIdentifier paramIdEnum = ParameterIdentifier.fromId(paramId);
                log.info("PI {} found which is {} and has a size of {} bytes", paramId, paramIdEnum, paramLength);
                ParameterValue paramValue = new ParameterValue(paramIdEnum, paramData);
                fpdu.getParameters().add(paramValue);
            } else if (ParameterGroupIdentifier.fromId(paramId) != null) {
                ParameterGroupIdentifier groupId = ParameterGroupIdentifier.fromId(paramId);
                log.info("PGI {} found which is {}", paramId, groupId);
                ParameterValue groupParameterValue = new ParameterValue(groupId, new ParameterValue[0]);
                fpdu.getParameters().add(groupParameterValue);
                ByteBuffer groupBuffer = ByteBuffer.wrap(paramData);
                while (groupBuffer.hasRemaining()) {
                    int groupParamId = groupBuffer.get();
                    int groupParamLength = groupBuffer.get();
                    byte[] groupParamData = new byte[groupParamLength];
                    groupBuffer.get(groupParamData);
                    ParameterValue groupParamValue = new ParameterValue(ParameterIdentifier.fromId(groupParamId),
                            groupParamData);
                    log.info("PI {} found which is {}", groupParamId, groupParamValue.getParameter());
                    groupParameterValue.getValues().add(groupParamValue);
                }
            } else {
                throw new IllegalArgumentException("Unknown parameter ID: " + paramId);
            }
        }
        return fpdu;
    }
}
