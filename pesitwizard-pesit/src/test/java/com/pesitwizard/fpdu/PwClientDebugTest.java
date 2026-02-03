package com.pesitwizard.fpdu;

import java.nio.file.Files;
import java.nio.file.Paths;
import javax.net.ssl.*;
import java.security.KeyStore;
import java.io.*;

public class PwClientDebugTest {
    public static void main(String[] args) throws Exception {
        String truststorePath = "/home/cpo/pesit/pesitwizard/integration-tests/cx-integration/docker/certs/ca-truststore.p12";
        
        // Load truststore
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(truststorePath)) {
            trustStore.load(fis, "changeit".toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustStore);
        
        SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        ctx.init(null, tmf.getTrustManagers(), null);
        
        SSLSocketFactory factory = ctx.getSocketFactory();
        SSLSocket socket = (SSLSocket) factory.createSocket("localhost", 5012);
        socket.setEnabledProtocols(new String[]{"TLSv1.2"});
        
        System.out.println("TLS connected");
        
        // Build CONNECT using PW's FpduBuilder
        Fpdu connectFpdu = new ConnectMessageBuilder()
            .demandeur("PWSRV01")
            .serveur("CETOM1")
            .writeAccess()
            .syncIntervalKb(256)
            .syncAckWindow(1)
            .build(1);
        
        byte[] fpduBytes = FpduBuilder.buildFpdu(connectFpdu);
        
        System.out.println("FpduBuilder output: " + fpduBytes.length + " bytes");
        System.out.print("Hex: ");
        for (byte b : fpduBytes) System.out.printf("%02X ", b);
        System.out.println();
        
        // Check internal length vs actual length
        int internalLen = ((fpduBytes[0] & 0xFF) << 8) | (fpduBytes[1] & 0xFF);
        System.out.println("Internal length field: " + internalLen);
        System.out.println("Array length: " + fpduBytes.length);
        System.out.println("Match: " + (internalLen == fpduBytes.length));
        
        // Now simulate what AbstractSocketTransportChannel.send() does:
        System.out.println("\n=== What PW transport sends ===");
        System.out.println("Transport adds: writeShort(" + fpduBytes.length + ") = 0x" + 
            String.format("%04X", fpduBytes.length));
        System.out.println("Then writes: " + fpduBytes.length + " bytes starting with 0x" +
            String.format("%02X%02X", fpduBytes[0], fpduBytes[1]));
        System.out.println("Wire format: [" + String.format("%04X", fpduBytes.length) + "]" +
            "[" + String.format("%02X%02X%02X%02X", fpduBytes[0], fpduBytes[1], fpduBytes[2], fpduBytes[3]) + "]...");
        System.out.println("This is DOUBLE LENGTH PREFIX!");
        
        // Send CORRECTLY (single length, which is already in fpduBytes)
        System.out.println("\n=== Sending correctly (single length) ===");
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.write(fpduBytes);  // fpduBytes already has length prefix
        out.flush();
        
        System.out.println("Sent, reading response...");
        DataInputStream in = new DataInputStream(socket.getInputStream());
        socket.setSoTimeout(5000);
        
        int respLen = in.readUnsignedShort();
        byte[] response = new byte[respLen];
        in.readFully(response);
        
        System.out.println("Response length: " + respLen);
        System.out.print("Response: ");
        for (byte b : response) System.out.printf("%02X ", b);
        System.out.println();
        
        int phase = response[0] & 0xFF;
        int type = response[1] & 0xFF;
        System.out.printf("Phase: 0x%02X, Type: 0x%02X%n", phase, type);
        
        if (type == 0x21) {
            System.out.println("*** ACONNECT - SUCCESS! ***");
        } else if (type == 0x25) {
            System.out.println("ABORT received");
        }
        
        socket.close();
    }
}
