package com.pesitwizard.server.service;

import static org.junit.jupiter.api.Assertions.*;

import com.pesitwizard.server.service.PathPlaceholderService.PlaceholderContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PathPlaceholderService Tests")
class PathPlaceholderServiceTest {

    private PathPlaceholderService service;

    @BeforeEach
    void setUp() {
        service = new PathPlaceholderService();
    }

    @Nested
    @DisplayName("Path Resolution Tests")
    class PathResolutionTests {

        @Test
        @DisplayName("Should return null path unchanged")
        void shouldReturnNullUnchanged() {
            assertNull(service.resolvePath(null, PlaceholderContext.builder().build()));
        }

        @Test
        @DisplayName("Should return path without placeholders unchanged")
        void shouldReturnPathWithoutPlaceholdersUnchanged() {
            String path = "/data/files/test.txt";
            String result = service.resolvePath(path, PlaceholderContext.builder().build());
            assertEquals(path, result);
        }

        @Test
        @DisplayName("Should resolve partner placeholder")
        void shouldResolvePartnerPlaceholder() {
            PlaceholderContext context =
                    PlaceholderContext.builder().partnerId("PARTNER_A").build();

            String result = service.resolvePath("/data/${partner}/files", context);
            assertEquals("/data/PARTNER_A/files", result);
        }

        @Test
        @DisplayName("Should resolve partnerId placeholder")
        void shouldResolvePartnerIdPlaceholder() {
            PlaceholderContext context =
                    PlaceholderContext.builder().partnerId("PARTNER_B").build();

            String result = service.resolvePath("/data/${partnerId}/files", context);
            assertEquals("/data/PARTNER_B/files", result);
        }

        @Test
        @DisplayName("Should resolve virtualFile placeholder")
        void shouldResolveVirtualFilePlaceholder() {
            PlaceholderContext context =
                    PlaceholderContext.builder().virtualFile("FILE001").build();

            String result = service.resolvePath("/data/${virtualFile}.dat", context);
            assertEquals("/data/FILE001.dat", result);
        }

        @Test
        @DisplayName("Should resolve transferId placeholder")
        void shouldResolveTransferIdPlaceholder() {
            PlaceholderContext context = PlaceholderContext.builder().transferId(12345L).build();

            String result = service.resolvePath("/data/transfer_${transferId}.dat", context);
            assertEquals("/data/transfer_12345.dat", result);
        }

        @Test
        @DisplayName("Should resolve direction placeholder")
        void shouldResolveDirectionPlaceholder() {
            PlaceholderContext context = PlaceholderContext.builder().direction("RECEIVE").build();

            String result = service.resolvePath("/data/${direction}/files", context);
            assertEquals("/data/RECEIVE/files", result);
        }

        @Test
        @DisplayName("Should resolve multiple placeholders")
        void shouldResolveMultiplePlaceholders() {
            PlaceholderContext context =
                    PlaceholderContext.builder()
                            .partnerId("PARTNER_X")
                            .virtualFile("DATA_FILE")
                            .direction("SEND")
                            .build();

            String result =
                    service.resolvePath(
                            "/data/${partner}/${direction}/${virtualFile}.txt", context);
            assertEquals("/data/PARTNER_X/SEND/DATA_FILE.txt", result);
        }

        @Test
        @DisplayName("Should resolve timestamp placeholder")
        void shouldResolveTimestampPlaceholder() {
            String result =
                    service.resolvePath(
                            "/data/file_${timestamp}.dat", PlaceholderContext.builder().build());
            // Should contain date pattern like 20231223_123456
            assertTrue(result.matches("/data/file_\\d{8}_\\d{6}\\.dat"));
        }

        @Test
        @DisplayName("Should resolve date placeholder")
        void shouldResolveDatePlaceholder() {
            String result =
                    service.resolvePath(
                            "/data/${date}/file.dat", PlaceholderContext.builder().build());
            assertTrue(result.matches("/data/\\d{8}/file\\.dat"));
        }

        @Test
        @DisplayName("Should resolve time placeholder")
        void shouldResolveTimePlaceholder() {
            String result =
                    service.resolvePath(
                            "/data/file_${time}.dat", PlaceholderContext.builder().build());
            assertTrue(result.matches("/data/file_\\d{6}\\.dat"));
        }

        @Test
        @DisplayName("Should resolve year placeholder")
        void shouldResolveYearPlaceholder() {
            String result =
                    service.resolvePath(
                            "/data/${year}/file.dat", PlaceholderContext.builder().build());
            assertTrue(result.matches("/data/\\d{4}/file\\.dat"));
        }

        @Test
        @DisplayName("Should resolve month placeholder")
        void shouldResolveMonthPlaceholder() {
            String result =
                    service.resolvePath(
                            "/data/${month}/file.dat", PlaceholderContext.builder().build());
            assertTrue(result.matches("/data/\\d{2}/file\\.dat"));
        }

        @Test
        @DisplayName("Should resolve day placeholder")
        void shouldResolveDayPlaceholder() {
            String result =
                    service.resolvePath(
                            "/data/${day}/file.dat", PlaceholderContext.builder().build());
            assertTrue(result.matches("/data/\\d{2}/file\\.dat"));
        }

        @Test
        @DisplayName("Should resolve hour placeholder")
        void shouldResolveHourPlaceholder() {
            String result =
                    service.resolvePath(
                            "/data/${hour}/file.dat", PlaceholderContext.builder().build());
            assertTrue(result.matches("/data/\\d{2}/file\\.dat"));
        }

        @Test
        @DisplayName("Should resolve minute placeholder")
        void shouldResolveMinutePlaceholder() {
            String result =
                    service.resolvePath(
                            "/data/${minute}/file.dat", PlaceholderContext.builder().build());
            assertTrue(result.matches("/data/\\d{2}/file\\.dat"));
        }

        @Test
        @DisplayName("Should resolve second placeholder")
        void shouldResolveSecondPlaceholder() {
            String result =
                    service.resolvePath(
                            "/data/${second}/file.dat", PlaceholderContext.builder().build());
            assertTrue(result.matches("/data/\\d{2}/file\\.dat"));
        }

        @Test
        @DisplayName("Should resolve uuid placeholder")
        void shouldResolveUuidPlaceholder() {
            String result =
                    service.resolvePath("/data/${uuid}.dat", PlaceholderContext.builder().build());
            // UUID format: 8-4-4-4-12 hex chars
            assertTrue(
                    result.matches(
                            "/data/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.dat"));
        }

        @Test
        @DisplayName("Should keep unknown placeholders unchanged")
        void shouldKeepUnknownPlaceholdersUnchanged() {
            String result =
                    service.resolvePath(
                            "/data/${unknown}/file.dat", PlaceholderContext.builder().build());
            assertEquals("/data/${unknown}/file.dat", result);
        }

        @Test
        @DisplayName("Should handle case-insensitive placeholders")
        void shouldHandleCaseInsensitivePlaceholders() {
            PlaceholderContext context = PlaceholderContext.builder().partnerId("TEST").build();

            String result1 = service.resolvePath("/data/${PARTNER}", context);
            String result2 = service.resolvePath("/data/${Partner}", context);
            String result3 = service.resolvePath("/data/${partner}", context);

            assertEquals("/data/TEST", result1);
            assertEquals("/data/TEST", result2);
            assertEquals("/data/TEST", result3);
        }
    }

    @Nested
    @DisplayName("PlaceholderContext Tests")
    class PlaceholderContextTests {

        @Test
        @DisplayName("Should build context with all fields")
        void shouldBuildContextWithAllFields() {
            PlaceholderContext context =
                    PlaceholderContext.builder()
                            .partnerId("PARTNER")
                            .virtualFile("FILE")
                            .transferId(123L)
                            .direction("SEND")
                            .build();

            assertEquals("PARTNER", context.getPartnerId());
            assertEquals("FILE", context.getVirtualFile());
            assertEquals(123L, context.getTransferId());
            assertEquals("SEND", context.getDirection());
        }

        @Test
        @DisplayName("Should handle null values in context")
        void shouldHandleNullValuesInContext() {
            PlaceholderContext context = PlaceholderContext.builder().build();

            assertNull(context.getPartnerId());
            assertNull(context.getVirtualFile());
            assertNull(context.getTransferId());
            assertNull(context.getDirection());
        }
    }
}
