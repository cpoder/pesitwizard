package com.pesitwizard.fpdu;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for ParameterBuilder. */
@DisplayName("ParameterBuilder Tests")
class ParameterBuilderTest {

    @Nested
    @DisplayName("Factory Method")
    class FactoryMethodTests {

        @Test
        @DisplayName("should create builder for parameter")
        void shouldCreateBuilderForParameter() {
            ParameterBuilder builder =
                    ParameterBuilder.forParameter(ParameterIdentifier.PI_12_NOM_FICHIER);
            assertThat(builder).isNotNull();
            assertThat(builder.getPI()).isEqualTo(ParameterIdentifier.PI_12_NOM_FICHIER);
        }
    }

    @Nested
    @DisplayName("Value Setting")
    class ValueSettingTests {

        @Test
        @DisplayName("should set byte array value")
        void shouldSetByteArrayValue() {
            byte[] value = new byte[] {1, 2, 3};
            ParameterBuilder builder =
                    ParameterBuilder.forParameter(ParameterIdentifier.PI_12_NOM_FICHIER)
                            .value(value);
            assertThat(builder.getValue()).isEqualTo(value);
        }

        @Test
        @DisplayName("should set string value")
        void shouldSetStringValue() {
            ParameterBuilder builder =
                    ParameterBuilder.forParameter(ParameterIdentifier.PI_12_NOM_FICHIER)
                            .value("test.txt");
            assertThat(builder.getValue()).isNotNull();
            assertThat(new String(builder.getValue())).isEqualTo("test.txt");
        }

        @Test
        @DisplayName("should set small integer value using minimum bytes")
        void shouldSetSmallIntegerValue() {
            // PI_13 has declared length 3 - variable-length encoding uses minimum bytes
            ParameterBuilder builder =
                    ParameterBuilder.forParameter(ParameterIdentifier.PI_13_ID_TRANSFERT).value(42);
            assertThat(builder.getValue()).isNotNull();
            assertThat(builder.getValue().length).isEqualTo(1);
            assertThat(builder.getValue()).isEqualTo(new byte[] {42});
        }

        @Test
        @DisplayName("should set medium integer value using minimum bytes")
        void shouldSetMediumIntegerValue() {
            // 1000 needs 2 bytes (0x03E8) even though PI_13 allows up to 3
            ParameterBuilder builder =
                    ParameterBuilder.forParameter(ParameterIdentifier.PI_13_ID_TRANSFERT)
                            .value(1000);
            assertThat(builder.getValue()).isNotNull();
            assertThat(builder.getValue().length).isEqualTo(2);
            // Verify value is correct (big-endian: 1000 = 0x03E8)
            assertThat(builder.getValue()).isEqualTo(new byte[] {3, (byte) 0xE8});
        }

        @Test
        @DisplayName("should set large integer value")
        void shouldSetLargeIntegerValue() {
            ParameterBuilder builder =
                    ParameterBuilder.forParameter(ParameterIdentifier.PI_25_TAILLE_MAX_ENTITE)
                            .value(4096);
            assertThat(builder.getValue()).isNotNull();
        }

        @Test
        @DisplayName("should set small long value")
        void shouldSetSmallLongValue() {
            ParameterBuilder builder =
                    ParameterBuilder.forParameter(ParameterIdentifier.PI_13_ID_TRANSFERT)
                            .value(100L);
            assertThat(builder.getValue()).isNotNull();
        }

        @Test
        @DisplayName("should set medium long value")
        void shouldSetMediumLongValue() {
            ParameterBuilder builder =
                    ParameterBuilder.forParameter(ParameterIdentifier.PI_25_TAILLE_MAX_ENTITE)
                            .value(50000L);
            assertThat(builder.getValue()).isNotNull();
        }

        @Test
        @DisplayName("should encode value using minimum bytes for files > 4GB")
        void shouldEncode5ByteValueForLargeFiles() {
            // 5 GB = 5,368,709,120 bytes (> 4GB = 4,294,967,296) needs 5 bytes
            long fiveGb = 5L * 1024 * 1024 * 1024;
            ParameterBuilder builder =
                    ParameterBuilder.forParameter(ParameterIdentifier.PI_27_NB_OCTETS)
                            .value(fiveGb);
            assertThat(builder.getValue()).hasSize(5);
            // Verify round-trip
            long decoded = 0;
            for (byte b : builder.getValue()) {
                decoded = (decoded << 8) | (b & 0xFF);
            }
            assertThat(decoded).isEqualTo(fiveGb);
        }

        @Test
        @DisplayName("should encode value using minimum bytes for very large files")
        void shouldEncode8ByteValueForVeryLargeFiles() {
            // 1 PB = 1,125,899,906,842,624 bytes needs 7 bytes
            long onePb = 1L * 1024 * 1024 * 1024 * 1024 * 1024;
            ParameterBuilder builder =
                    ParameterBuilder.forParameter(ParameterIdentifier.PI_27_NB_OCTETS).value(onePb);
            assertThat(builder.getValue()).hasSize(7);
            long decoded = 0;
            for (byte b : builder.getValue()) {
                decoded = (decoded << 8) | (b & 0xFF);
            }
            assertThat(decoded).isEqualTo(onePb);
        }

        @Test
        @DisplayName("should reject long value exceeding PI max length")
        void shouldRejectLongValueExceedingPiMaxLength() {
            // PI_25 has max length 2 (max 65535), try a value that needs 3 bytes
            assertThatThrownBy(
                            () ->
                                    ParameterBuilder.forParameter(
                                                    ParameterIdentifier.PI_25_TAILLE_MAX_ENTITE)
                                            .value(100_000L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requires 3 bytes");
        }
    }

    @Nested
    @DisplayName("Build Method")
    class BuildMethodTests {

        @Test
        @DisplayName("should build parameter with short value")
        void shouldBuildParameterWithShortValue() throws Exception {
            byte[] result =
                    ParameterBuilder.forParameter(ParameterIdentifier.PI_12_NOM_FICHIER)
                            .value("test.txt")
                            .build();

            assertThat(result).isNotNull();
            assertThat(result[0]).isEqualTo((byte) ParameterIdentifier.PI_12_NOM_FICHIER.getId());
            assertThat(result[1]).isEqualTo((byte) 8); // length of "test.txt"
        }

        @Test
        @DisplayName("should throw exception when value not set")
        void shouldThrowExceptionWhenValueNotSet() {
            ParameterBuilder builder =
                    ParameterBuilder.forParameter(ParameterIdentifier.PI_12_NOM_FICHIER);

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PI value not set");
        }

        @Test
        @DisplayName("should handle long values requiring extended length")
        void shouldHandleLongValuesRequiringExtendedLength() throws Exception {
            // Create a value longer than 254 bytes
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 260; i++) {
                sb.append("X");
            }

            byte[] result =
                    ParameterBuilder.forParameter(ParameterIdentifier.PI_12_NOM_FICHIER)
                            .value(sb.toString())
                            .build();

            assertThat(result).isNotNull();
            assertThat(result[1] & 0xFF).isEqualTo(0xFF); // Extended length indicator
        }
    }

    @Nested
    @DisplayName("Getter Methods")
    class GetterMethodTests {

        @Test
        @DisplayName("should return PI via getPI method")
        void shouldReturnPiViaGetPiMethod() {
            ParameterBuilder builder =
                    ParameterBuilder.forParameter(ParameterIdentifier.PI_06_VERSION);
            assertThat(builder.getPI()).isEqualTo(ParameterIdentifier.PI_06_VERSION);
        }
    }
}
