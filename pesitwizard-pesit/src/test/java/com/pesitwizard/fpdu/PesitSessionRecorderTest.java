package com.pesitwizard.fpdu;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for PesitSessionRecorder.
 */
@DisplayName("PesitSessionRecorder Tests")
class PesitSessionRecorderTest {

    @TempDir
    Path tempDir;

    private PesitSessionRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new PesitSessionRecorder("test-session");
    }

    @Nested
    @DisplayName("Constructor and Getters")
    class ConstructorTests {

        @Test
        @DisplayName("should create recorder with session name")
        void shouldCreateRecorderWithSessionName() {
            assertThat(recorder.getSessionName()).isEqualTo("test-session");
        }

        @Test
        @DisplayName("should have empty frames initially")
        void shouldHaveEmptyFramesInitially() {
            assertThat(recorder.getFrames()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Recording FPDUs")
    class RecordingTests {

        @Test
        @DisplayName("should record raw FPDU with type")
        void shouldRecordRawFpduWithType() {
            byte[] data = new byte[] { 0x40, 0x11, 0, 0, 0, 0 }; // CONNECT FPDU bytes
            recorder.recordRaw(PesitSessionRecorder.Direction.SENT, FpduType.CONNECT, data);

            assertThat(recorder.getFrames()).hasSize(1);
            assertThat(recorder.getFrames().get(0).direction())
                    .isEqualTo(PesitSessionRecorder.Direction.SENT);
            assertThat(recorder.getFrames().get(0).type()).isEqualTo(FpduType.CONNECT);
        }

        @Test
        @DisplayName("should record raw bytes with type")
        void shouldRecordRawBytesWithType() {
            byte[] data = new byte[] { 0x40, 0x21, 0, 0, 0, 0 };
            recorder.recordRaw(PesitSessionRecorder.Direction.RECEIVED, FpduType.ACONNECT, data);

            assertThat(recorder.getFrames()).hasSize(1);
            assertThat(recorder.getFrames().get(0).type()).isEqualTo(FpduType.ACONNECT);
        }

        @Test
        @DisplayName("should record raw bytes and parse type")
        void shouldRecordRawBytesAndParseType() {
            byte[] data = new byte[] { 0x40, 0x21, 0, 0, 0, 0 };
            recorder.recordRaw(PesitSessionRecorder.Direction.RECEIVED, data);

            assertThat(recorder.getFrames()).hasSize(1);
        }

        @Test
        @DisplayName("should record raw bytes with parse failure")
        void shouldRecordRawBytesWithParseFailure() {
            byte[] data = new byte[] { 1, 2 }; // Too short to parse
            recorder.recordRaw(PesitSessionRecorder.Direction.RECEIVED, data);

            assertThat(recorder.getFrames()).hasSize(1);
            assertThat(recorder.getFrames().get(0).type()).isNull();
        }
    }

    @Nested
    @DisplayName("Clear Method")
    class ClearTests {

        @Test
        @DisplayName("should clear all frames")
        void shouldClearAllFrames() {
            recorder.recordRaw(PesitSessionRecorder.Direction.SENT, FpduType.CONNECT, new byte[6]);
            recorder.recordRaw(PesitSessionRecorder.Direction.RECEIVED, FpduType.ACONNECT, new byte[6]);

            assertThat(recorder.getFrames()).hasSize(2);

            recorder.clear();

            assertThat(recorder.getFrames()).isEmpty();
        }
    }

    @Nested
    @DisplayName("File Operations")
    class FileOperationsTests {

        @Test
        @DisplayName("should save and load raw frames")
        void shouldSaveAndLoadRawFrames() throws Exception {
            byte[] data = new byte[] { 0x40, 0x21, 0, 0, 0, 0 };
            recorder.recordRaw(PesitSessionRecorder.Direction.SENT, FpduType.CONNECT, data);
            recorder.recordRaw(PesitSessionRecorder.Direction.RECEIVED, FpduType.ACONNECT, data);

            Path filePath = tempDir.resolve("session.raw");
            recorder.saveRawToFile(filePath);

            assertThat(Files.exists(filePath)).isTrue();

            PesitSessionRecorder loaded = PesitSessionRecorder.loadRawFromFile(filePath);
            assertThat(loaded.getFrames()).hasSize(2);
        }

        @Test
        @DisplayName("should save and load serialized frames")
        void shouldSaveAndLoadSerializedFrames() throws Exception {
            byte[] data = new byte[] { 0x40, 0x21, 0, 0, 0, 0 };
            recorder.recordRaw(PesitSessionRecorder.Direction.SENT, FpduType.CONNECT, data);

            Path filePath = tempDir.resolve("session.ser");
            recorder.saveToFile(filePath);

            assertThat(Files.exists(filePath)).isTrue();

            PesitSessionRecorder loaded = PesitSessionRecorder.loadFromFile(filePath);
            assertThat(loaded.getSessionName()).isEqualTo("test-session");
            assertThat(loaded.getFrames()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("RecordedFrame Record")
    class RecordedFrameTests {

        @Test
        @DisplayName("should format toString correctly")
        void shouldFormatToStringCorrectly() {
            byte[] data = new byte[10];
            PesitSessionRecorder.RecordedFrame frame = new PesitSessionRecorder.RecordedFrame(
                    java.time.Instant.now(),
                    PesitSessionRecorder.Direction.SENT,
                    FpduType.CONNECT,
                    data);

            String str = frame.toString();
            assertThat(str).contains("SENT");
            assertThat(str).contains("CONNECT");
            assertThat(str).contains("10 bytes");
        }
    }

    @Nested
    @DisplayName("Direction Enum")
    class DirectionEnumTests {

        @Test
        @DisplayName("should have SENT and RECEIVED values")
        void shouldHaveSentAndReceivedValues() {
            assertThat(PesitSessionRecorder.Direction.SENT).isNotNull();
            assertThat(PesitSessionRecorder.Direction.RECEIVED).isNotNull();
            assertThat(PesitSessionRecorder.Direction.values()).hasSize(2);
        }
    }
}
