package com.pesitwizard.fpdu;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SelectMessageBuilder.
 */
@DisplayName("SelectMessageBuilder Tests")
class SelectMessageBuilderTest {

    private SelectMessageBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SelectMessageBuilder();
    }

    @Nested
    @DisplayName("Builder Methods")
    class BuilderMethodsTests {

        @Test
        @DisplayName("should set filename")
        void shouldSetFilename() {
            SelectMessageBuilder result = builder.filename("test-file.dat");
            assertThat(result).isSameAs(builder);
        }

        @Test
        @DisplayName("should set transfer ID")
        void shouldSetTransferId() {
            SelectMessageBuilder result = builder.transferId(42);
            assertThat(result).isSameAs(builder);
        }

        @Test
        @DisplayName("should set restart flag")
        void shouldSetRestartFlag() {
            SelectMessageBuilder result = builder.restart();
            assertThat(result).isSameAs(builder);
        }

        @Test
        @DisplayName("should set file type")
        void shouldSetFileType() {
            SelectMessageBuilder result = builder.fileType(1);
            assertThat(result).isSameAs(builder);
        }
    }

    @Nested
    @DisplayName("Build FPDU")
    class BuildTests {

        @Test
        @DisplayName("should build SELECT FPDU with default values")
        void shouldBuildSelectFpduWithDefaultValues() throws Exception {
            Fpdu fpdu = builder.build(1);

            assertThat(fpdu).isNotNull();
            assertThat(fpdu.getFpduType()).isEqualTo(FpduType.SELECT);
        }

        @Test
        @DisplayName("should build SELECT FPDU with custom filename")
        void shouldBuildSelectFpduWithCustomFilename() throws Exception {
            Fpdu fpdu = builder
                    .filename("my-file.txt")
                    .build(1);

            assertThat(fpdu).isNotNull();
            assertThat(fpdu.getFpduType()).isEqualTo(FpduType.SELECT);
        }

        @Test
        @DisplayName("should build SELECT FPDU with transfer ID")
        void shouldBuildSelectFpduWithTransferId() throws Exception {
            Fpdu fpdu = builder
                    .transferId(123)
                    .build(1);

            assertThat(fpdu).isNotNull();
        }

        @Test
        @DisplayName("should build SELECT FPDU with restart flag")
        void shouldBuildSelectFpduWithRestartFlag() throws Exception {
            Fpdu fpdu = builder
                    .restart()
                    .build(1);

            assertThat(fpdu).isNotNull();
        }

        @Test
        @DisplayName("should build SELECT FPDU with file type")
        void shouldBuildSelectFpduWithFileType() throws Exception {
            Fpdu fpdu = builder
                    .fileType(2)
                    .build(1);

            assertThat(fpdu).isNotNull();
        }

        @Test
        @DisplayName("should build SELECT FPDU with all options")
        void shouldBuildSelectFpduWithAllOptions() throws Exception {
            Fpdu fpdu = builder
                    .filename("complete-file.dat")
                    .transferId(999)
                    .fileType(1)
                    .restart()
                    .build(5);

            assertThat(fpdu).isNotNull();
            assertThat(fpdu.getFpduType()).isEqualTo(FpduType.SELECT);
        }

        @Test
        @DisplayName("should set server connection ID")
        void shouldSetServerConnectionId() throws Exception {
            Fpdu fpdu = builder.build(7);

            assertThat(fpdu).isNotNull();
            assertThat(fpdu.getIdDst()).isEqualTo(7);
        }
    }
}
