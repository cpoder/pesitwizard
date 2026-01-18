package com.pesitwizard.fpdu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * FPDU (File Transfer Protocol Data Unit) Builder
 * Constructs binary PESIT protocol messages according to PESIT E specification
 */
public class FpduBuilder {
    public static byte[] buildFpdu(FpduType fpduType, int idDest, int idSrc, byte[] data) {
        ByteBuffer fpdu = ByteBuffer.allocate(6 + data.length);
        fpdu.putShort((short) (6 + data.length)); // Total length
        fpdu.put((byte) fpduType.getPhase());
        fpdu.put((byte) fpduType.getType());
        fpdu.put((byte) idDest);
        fpdu.put((byte) idSrc);
        fpdu.put(data);
        return fpdu.array();
    }

    public static byte[] buildFpdu(FpduType fpduType, int idDest, int idSrc) {
        return buildFpdu(fpduType, idDest, idSrc, new ParameterValue[0]);
    }

    /**
     * Build complete FPDU message with PI and PGI builders
     * 
     * @param fpduType FPDU type
     * @param idDest   Destination connection ID
     * @param idSrc    Source connection ID (or 0 for file-level FPDUs)
     * @param pis      Parameter Identifier builders
     * @param pgis     Parameter Group Identifier builders
     * @return Complete FPDU byte array
     */
    public static byte[] buildFpdu(FpduType fpduType, int idDest, int idSrc, ParameterValue... pis) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Parameter> parameters = new ArrayList<>();
        for (ParameterValue pi : pis) {
            try {
                out.write(pi.getBytes());
                parameters.add(pi.getParameter());
            } catch (IOException e) {
                throw new FpduBuildException("Failed to encode parameter " + pi.getParameter(), e);
            }
        }

        // Validate mandatory parameters after building
        for (ParameterRequirement req : fpduType.getParameterRequirements()) {
            if (req.isMandatory() && !parameters.contains(req.getParameter())) {
                throw new IllegalArgumentException("Missing mandatory PI: " + req.getParameter().toString());
            }
        }

        return buildFpdu(fpduType, idDest, idSrc, out.toByteArray());
    }

    public static byte[] buildFpdu(Fpdu fpdu) {
        return buildFpdu(fpdu.getFpduType(), fpdu.getIdDst(), fpdu.getIdSrc(),
                fpdu.getParameters().toArray(new ParameterValue[0]));
    }

    /**
     * Build a multi-article DTF FPDU for variable-length records.
     * Format: [total_length][phase][type][idDst][idSrc][len1][art1][len2][art2]...
     * Each article is prefixed with a 2-byte length.
     * 
     * @param idDest        Destination connection ID
     * @param articles      List of article data (each article is a byte array)
     * @param maxEntitySize Maximum entity size (PI 25) - total FPDU must not exceed
     *                      this
     * @return DTF FPDU bytes, or null if articles don't fit
     */
    public static byte[] buildMultiArticleDtf(int idDest, List<byte[]> articles, int maxEntitySize) {
        // Calculate total size: 6 (header) + sum of (2 + article.length) for each
        // article
        int dataSize = 0;
        for (byte[] article : articles) {
            dataSize += 2 + article.length; // 2-byte length prefix + article data
        }
        int totalSize = 6 + dataSize;

        if (totalSize > maxEntitySize) {
            return null; // Doesn't fit
        }

        ByteBuffer fpdu = ByteBuffer.allocate(totalSize);
        fpdu.putShort((short) totalSize); // Total length
        fpdu.put((byte) FpduType.DTF.getPhase());
        fpdu.put((byte) FpduType.DTF.getType());
        fpdu.put((byte) idDest);
        fpdu.put((byte) articles.size()); // idSrc = number of articles for multi-article DTF

        // Add each article with 2-byte length prefix
        for (byte[] article : articles) {
            fpdu.putShort((short) article.length);
            fpdu.put(article);
        }

        return fpdu.array();
    }

    /**
     * Calculate how many articles can fit in one entity.
     * 
     * @param articleSize   Size of each article
     * @param maxEntitySize Maximum entity size (PI 25)
     * @return Number of articles that fit (minimum 1)
     */
    public static int calculateArticlesPerEntity(int articleSize, int maxEntitySize) {
        // Each article needs: 2 (length prefix) + articleSize
        // Entity overhead: 6 (FPDU header)
        int availableSpace = maxEntitySize - 6;
        int articleWithPrefix = 2 + articleSize;
        return Math.max(1, availableSpace / articleWithPrefix);
    }
}
