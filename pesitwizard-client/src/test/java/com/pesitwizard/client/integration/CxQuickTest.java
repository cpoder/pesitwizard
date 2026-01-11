package com.pesitwizard.client.integration;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;

import com.pesitwizard.client.pesit.FpduReader;
import com.pesitwizard.client.pesit.FpduWriter;
import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.CreateMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterGroupIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.session.PesitSession;
import com.pesitwizard.transport.TcpTransportChannel;

public class CxQuickTest {
        static final String HOST = "localhost";
        static final int PORT = 5100;
        static final String SERVER = "CETOM1";
        static final String PARTNER = "LOOP";
        static final String VFILE = "FILE";
        static final String VFILE_PULL = "BIG"; // For PULL tests
        static final String VFILE_SYNC = "SYNCIN"; // For sync/restart tests (fixed physical path)

        public static void main(String[] args) throws Exception {
                System.out.println("=== C:X PUSH TEST ===");
                testPush();
                Thread.sleep(500);
                System.out.println("\n=== C:X PUSH WITH SYNC TEST ===");
                testPushWithSync();
                Thread.sleep(500);
                System.out.println("\n=== C:X PUSH SYNC+RESTART TEST (SYNCIN) ===");
                testPushSyncRestart();
                Thread.sleep(500);
                System.out.println("\n=== C:X PULL TEST ===");
                testPull();
                System.out.println("=== ALL TESTS SUCCESS ===");
        }

        static void testPushWithSync() throws Exception {
                byte[] data = new byte[100 * 1024]; // 100KB
                new java.util.Random(42).nextBytes(data);
                TcpTransportChannel ch = new TcpTransportChannel(HOST, PORT);
                ch.setReceiveTimeout(30000);
                try (PesitSession s = new PesitSession(ch, false)) {
                        Fpdu ac = s.sendFpduWithAck(new ConnectMessageBuilder()
                                        .demandeur(PARTNER).serveur(SERVER).writeAccess()
                                        .syncPointsEnabled(true).syncIntervalKb(10).resyncEnabled(true)
                                        .build(1));
                        int dst = ac.getIdSrc();
                        ParameterValue pi7 = ac.getParameter(PI_07_SYNC_POINTS);
                        int syncKb = (pi7 != null && pi7.getValue() != null && pi7.getValue().length >= 2)
                                        ? ((pi7.getValue()[0] & 0xFF) << 8 | (pi7.getValue()[1] & 0xFF))
                                        : 0;
                        boolean syncEnabled = syncKb > 0;
                        long syncIntervalBytes = syncKb * 1024L;
                        System.out.println("  Negotiated sync: " + syncKb + "KB = " + syncIntervalBytes + " bytes");
                        if (!syncEnabled) {
                                System.out.println("  WARN: Sync disabled by server");
                        }
                        s.sendFpduWithAck(new CreateMessageBuilder().filename(VFILE)
                                        .transferId(99).variableFormat().recordLength(506)
                                        .maxEntitySize(512).fileSizeKB(100).build(dst));
                        s.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(dst));
                        s.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(dst));
                        FpduWriter w = new FpduWriter(s, dst, 512, 506, false);
                        int syncNum = 0;
                        long bytesSinceSync = 0;
                        int off = 0;
                        while (off < data.length) {
                                // Envoyer sync AVANT de dépasser l'intervalle négocié
                                if (syncEnabled && bytesSinceSync > 0 && bytesSinceSync + 506 > syncIntervalBytes) {
                                        syncNum++;
                                        s.sendFpduWithAck(new Fpdu(FpduType.SYN).withIdDst(dst)
                                                        .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncNum)));
                                        System.out.println("  Sync " + syncNum + " at " + off + " bytes");
                                        bytesSinceSync = 0;
                                }
                                int sz = Math.min(506, data.length - off);
                                byte[] chunk = new byte[sz];
                                System.arraycopy(data, off, chunk, 0, sz);
                                w.writeDtf(chunk);
                                off += sz;
                                bytesSinceSync += sz;
                        }
                        System.out.println("  Sent: " + w.getTotalBytesSent() + " with " + syncNum + " syncs");
                        s.sendFpdu(new Fpdu(FpduType.DTF_END).withIdDst(dst)
                                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
                        s.sendFpduWithAck(new Fpdu(FpduType.TRANS_END).withIdDst(dst));
                        s.sendFpduWithAck(new Fpdu(FpduType.CLOSE).withIdDst(dst)
                                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
                        s.sendFpduWithAck(new Fpdu(FpduType.DESELECT).withIdDst(dst)
                                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
                        s.sendFpduWithAck(new Fpdu(FpduType.RELEASE).withIdDst(dst).withIdSrc(1)
                                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
                }
        }

        static void testPull() throws Exception {
                TcpTransportChannel ch = new TcpTransportChannel(HOST, PORT);
                ch.setReceiveTimeout(30000);
                try (PesitSession s = new PesitSession(ch, false)) {
                        Fpdu ac = s.sendFpduWithAck(new ConnectMessageBuilder()
                                        .demandeur(PARTNER).serveur(SERVER).readAccess().build(1));
                        int dst = ac.getIdSrc();
                        int tid = (int) (System.currentTimeMillis() % 0xFFFFFF);
                        ParameterValue pgi9 = new ParameterValue(ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                                        new ParameterValue(PI_11_TYPE_FICHIER, 0),
                                        new ParameterValue(PI_12_NOM_FICHIER, VFILE_PULL));
                        s.sendFpduWithAck(new Fpdu(FpduType.SELECT).withIdDst(dst)
                                        .withParameter(pgi9)
                                        .withParameter(new ParameterValue(PI_13_ID_TRANSFERT, tid))
                                        .withParameter(new ParameterValue(PI_14_ATTRIBUTS_DEMANDES, 0))
                                        .withParameter(new ParameterValue(PI_17_PRIORITE, 0))
                                        .withParameter(new ParameterValue(PI_25_TAILLE_MAX_ENTITE, 512)));
                        s.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(dst));
                        s.sendFpduWithAck(new Fpdu(FpduType.READ).withIdDst(dst)
                                        .withParameter(new ParameterValue(PI_18_POINT_RELANCE, 0)));
                        // Receive DTF
                        FpduReader r = new FpduReader(s);
                        long total = 0;
                        boolean receiving = true;
                        while (receiving) {
                                Fpdu f = r.read();
                                FpduType t = f.getFpduType();
                                if (t == FpduType.DTF || t == FpduType.DTFDA || t == FpduType.DTFMA
                                                || t == FpduType.DTFFA) {
                                        byte[] d = f.getData();
                                        if (d != null)
                                                total += d.length;
                                } else if (t == FpduType.SYN) {
                                        ParameterValue pi20 = f.getParameter(PI_20_NUM_SYNC);
                                        int sync = pi20 != null && pi20.getValue() != null
                                                        ? ((pi20.getValue()[0] & 0xFF) << 8
                                                                        | (pi20.getValue().length > 1
                                                                                        ? pi20.getValue()[1] & 0xFF
                                                                                        : 0))
                                                        : 1;
                                        s.sendFpdu(new Fpdu(FpduType.ACK_SYN).withIdDst(dst)
                                                        .withParameter(new ParameterValue(PI_20_NUM_SYNC, sync)));
                                } else if (t == FpduType.DTF_END || t == FpduType.TRANS_END) {
                                        receiving = false;
                                }
                        }
                        System.out.println("Received: " + total + " bytes");
                        // Cleanup: TRANS_END -> CLOSE -> DESELECT -> RELEASE
                        s.sendFpduWithAck(new Fpdu(FpduType.TRANS_END).withIdDst(dst));
                        s.sendFpduWithAck(new Fpdu(FpduType.CLOSE).withIdDst(dst)
                                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
                        s.sendFpduWithAck(new Fpdu(FpduType.DESELECT).withIdDst(dst)
                                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
                        s.sendFpduWithAck(new Fpdu(FpduType.RELEASE).withIdDst(dst).withIdSrc(1)
                                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
                }
        }

        static void testPush() throws Exception {
                byte[] data = new byte[10 * 1024];
                new java.util.Random(42).nextBytes(data);
                TcpTransportChannel ch = new TcpTransportChannel(HOST, PORT);
                ch.setReceiveTimeout(30000);
                try (PesitSession s = new PesitSession(ch, false)) {
                        Fpdu ac = s.sendFpduWithAck(new ConnectMessageBuilder()
                                        .demandeur(PARTNER).serveur(SERVER).writeAccess().build(1));
                        int dst = ac.getIdSrc();
                        s.sendFpduWithAck(new CreateMessageBuilder().filename(VFILE)
                                        .transferId(1).variableFormat().recordLength(506)
                                        .maxEntitySize(512).fileSizeKB(10).build(dst));
                        s.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(dst));
                        s.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(dst));
                        FpduWriter w = new FpduWriter(s, dst, 512, 506, false);
                        w.writeDtf(data);
                        System.out.println("Sent: " + w.getTotalBytesSent());
                        s.sendFpdu(new Fpdu(FpduType.DTF_END).withIdDst(dst)
                                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
                        s.sendFpduWithAck(new Fpdu(FpduType.TRANS_END).withIdDst(dst));
                        s.sendFpduWithAck(new Fpdu(FpduType.CLOSE).withIdDst(dst)
                                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
                        s.sendFpduWithAck(new Fpdu(FpduType.DESELECT).withIdDst(dst)
                                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
                        s.sendFpduWithAck(new Fpdu(FpduType.RELEASE).withIdDst(dst).withIdSrc(1)
                                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
                }
        }

        static void testPushSyncRestart() throws Exception {
                byte[] data = new byte[100 * 1024];
                new java.util.Random(42).nextBytes(data);
                int transferId = (int) (System.currentTimeMillis() % 0xFFFF);

                System.out.println("  Phase 1: Partial send...");
                int lastSync = 0;
                long lastBytes = 0;

                TcpTransportChannel ch1 = new TcpTransportChannel(HOST, PORT);
                ch1.setReceiveTimeout(30000);
                try (PesitSession s = new PesitSession(ch1, false)) {
                        Fpdu ac = s.sendFpduWithAck(new ConnectMessageBuilder().demandeur(PARTNER).serveur(SERVER)
                                        .writeAccess().syncPointsEnabled(true).syncIntervalKb(10).resyncEnabled(true)
                                        .build(1));
                        int dst = ac.getIdSrc();
                        ParameterValue pi7 = ac.getParameter(PI_07_SYNC_POINTS);
                        int syncKb = (pi7 != null && pi7.getValue() != null && pi7.getValue().length >= 2)
                                        ? ((pi7.getValue()[0] & 0xFF) << 8 | (pi7.getValue()[1] & 0xFF))
                                        : 0;
                        long syncInt = syncKb * 1024L;
                        System.out.println("  Sync interval: " + syncKb + "KB");

                        s.sendFpduWithAck(new CreateMessageBuilder().filename(VFILE_SYNC).transferId(transferId)
                                        .variableFormat().recordLength(506).maxEntitySize(512).fileSizeKB(100)
                                        .build(dst));
                        s.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(dst));
                        s.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(dst));

                        FpduWriter w = new FpduWriter(s, dst, 512, 506, false);
                        int syncNum = 0;
                        long sinceLast = 0;
                        int off = 0;
                        while (off < data.length && syncNum < 3) {
                                if (syncKb > 0 && sinceLast > 0 && sinceLast + 506 > syncInt) {
                                        syncNum++;
                                        s.sendFpduWithAck(new Fpdu(FpduType.SYN).withIdDst(dst)
                                                        .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncNum)));
                                        System.out.println("  Sync " + syncNum + " at " + off);
                                        lastSync = syncNum;
                                        lastBytes = off;
                                        sinceLast = 0;
                                }
                                int sz = Math.min(506, data.length - off);
                                byte[] c = new byte[sz];
                                System.arraycopy(data, off, c, 0, sz);
                                w.writeDtf(c);
                                off += sz;
                                sinceLast += sz;
                        }
                        System.out.println("  Interrupting at sync " + lastSync);
                }
                Thread.sleep(1000);

                System.out.println("  Phase 2: Resume from sync " + lastSync + "...");
                TcpTransportChannel ch2 = new TcpTransportChannel(HOST, PORT);
                ch2.setReceiveTimeout(30000);
                try (PesitSession s = new PesitSession(ch2, false)) {
                        Fpdu ac = s.sendFpduWithAck(new ConnectMessageBuilder().demandeur(PARTNER).serveur(SERVER)
                                        .writeAccess().syncPointsEnabled(true).syncIntervalKb(10).resyncEnabled(true)
                                        .build(1));
                        int dst = ac.getIdSrc();
                        ParameterValue pi7 = ac.getParameter(PI_07_SYNC_POINTS);
                        int syncKb = (pi7 != null && pi7.getValue() != null && pi7.getValue().length >= 2)
                                        ? ((pi7.getValue()[0] & 0xFF) << 8 | (pi7.getValue()[1] & 0xFF))
                                        : 0;
                        long syncInt = syncKb * 1024L;

                        s.sendFpduWithAck(new CreateMessageBuilder().filename(VFILE_SYNC).transferId(transferId)
                                        .variableFormat().recordLength(506).maxEntitySize(512).fileSizeKB(100)
                                        .build(dst));
                        s.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(dst));
                        s.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(dst)
                                        .withParameter(new ParameterValue(PI_18_POINT_RELANCE, lastSync)));

                        FpduWriter w = new FpduWriter(s, dst, 512, 506, false);
                        int syncNum = lastSync;
                        long sinceLast = 0;
                        int off = (int) lastBytes;
                        while (off < data.length) {
                                if (syncKb > 0 && sinceLast > 0 && sinceLast + 506 > syncInt) {
                                        syncNum++;
                                        s.sendFpduWithAck(new Fpdu(FpduType.SYN).withIdDst(dst)
                                                        .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncNum)));
                                        sinceLast = 0;
                                }
                                int sz = Math.min(506, data.length - off);
                                byte[] c = new byte[sz];
                                System.arraycopy(data, off, c, 0, sz);
                                w.writeDtf(c);
                                off += sz;
                                sinceLast += sz;
                        }
                        System.out.println("  Resume complete: " + w.getTotalBytesSent() + " bytes");
                        s.sendFpdu(new Fpdu(FpduType.DTF_END).withIdDst(dst)
                                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
                        s.sendFpduWithAck(new Fpdu(FpduType.TRANS_END).withIdDst(dst));
                        s.sendFpduWithAck(new Fpdu(FpduType.CLOSE).withIdDst(dst)
                                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
                        s.sendFpduWithAck(new Fpdu(FpduType.DESELECT).withIdDst(dst)
                                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
                        s.sendFpduWithAck(new Fpdu(FpduType.RELEASE).withIdDst(dst).withIdSrc(1)
                                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
                }
        }
}