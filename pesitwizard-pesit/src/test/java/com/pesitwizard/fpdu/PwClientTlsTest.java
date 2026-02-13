package com.pesitwizard.fpdu;

import com.pesitwizard.session.PesitSession;
import com.pesitwizard.transport.TlsTransportChannel;
import java.nio.file.Files;
import java.nio.file.Paths;

public class PwClientTlsTest {
    public static void main(String[] args) throws Exception {
        String truststorePath =
                "/home/cpo/pesit/pesitwizard/integration-tests/cx-integration/docker/certs/ca-truststore.p12";
        byte[] truststoreData = Files.readAllBytes(Paths.get(truststorePath));
        String truststorePassword = "changeit";

        System.out.println("Testing PW Client TLS connection to CX on port 5012...");

        TlsTransportChannel channel =
                new TlsTransportChannel("localhost", 5012, truststoreData, truststorePassword);

        try (PesitSession session = new PesitSession(channel)) {
            Fpdu connectFpdu =
                    new ConnectMessageBuilder()
                            .demandeur("PWSRV01")
                            .serveur("CETOM1")
                            .writeAccess()
                            .syncIntervalKb(256)
                            .syncAckWindow(1)
                            .build(1);

            System.out.println("Sending CONNECT...");
            Fpdu response = session.sendFpduWithAck(connectFpdu);
            System.out.println("Response: " + response.getFpduType());

            if (response.getFpduType() == FpduType.ACONNECT) {
                System.out.println("*** SUCCESS! PW Client TLS CONNECT accepted by CX! ***");
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
