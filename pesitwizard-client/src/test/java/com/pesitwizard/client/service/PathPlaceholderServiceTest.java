package com.pesitwizard.client.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.pesitwizard.client.service.PathPlaceholderService.PlaceholderContext;

@DisplayName("PathPlaceholderService Tests")
class PathPlaceholderServiceTest {

    private PathPlaceholderService service;

    @BeforeEach
    void setUp() {
        service = new PathPlaceholderService();
    }

    @Nested
    @DisplayName("resolvePath")
    class ResolvePathTests {

        @Test
        @DisplayName("should return null path as-is")
        void shouldReturnNullPathAsIs() {
            String result = service.resolvePath(null, PlaceholderContext.builder().build());
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return path without placeholders as-is")
        void shouldReturnPathWithoutPlaceholdersAsIs() {
            String path = "/data/transfers/file.txt";
            String result = service.resolvePath(path, PlaceholderContext.builder().build());
            assertThat(result).isEqualTo(path);
        }

        @Test
        @DisplayName("should resolve partner placeholder")
        void shouldResolvePartnerPlaceholder() {
            PlaceholderContext ctx = PlaceholderContext.builder()
                    .partnerId("CLIENT001")
                    .build();

            String result = service.resolvePath("/data/${partner}/files", ctx);
            assertThat(result).isEqualTo("/data/CLIENT001/files");
        }

        @Test
        @DisplayName("should resolve partnerId placeholder")
        void shouldResolvePartnerIdPlaceholder() {
            PlaceholderContext ctx = PlaceholderContext.builder()
                    .partnerId("PARTNER123")
                    .build();

            String result = service.resolvePath("/data/${partnerId}/files", ctx);
            assertThat(result).isEqualTo("/data/PARTNER123/files");
        }

        @Test
        @DisplayName("should resolve virtualFile placeholder")
        void shouldResolveVirtualFilePlaceholder() {
            PlaceholderContext ctx = PlaceholderContext.builder()
                    .virtualFile("VFILE01")
                    .build();

            String result = service.resolvePath("/data/${virtualFile}.dat", ctx);
            assertThat(result).isEqualTo("/data/VFILE01.dat");
        }

        @Test
        @DisplayName("should resolve server placeholder")
        void shouldResolveServerPlaceholder() {
            PlaceholderContext ctx = PlaceholderContext.builder()
                    .serverId("SRV001")
                    .build();

            String result = service.resolvePath("/data/${server}/incoming", ctx);
            assertThat(result).isEqualTo("/data/SRV001/incoming");
        }

        @Test
        @DisplayName("should resolve serverName placeholder")
        void shouldResolveServerNamePlaceholder() {
            PlaceholderContext ctx = PlaceholderContext.builder()
                    .serverName("Production")
                    .build();

            String result = service.resolvePath("/data/${serverName}/files", ctx);
            assertThat(result).isEqualTo("/data/Production/files");
        }

        @Test
        @DisplayName("should resolve direction placeholder")
        void shouldResolveDirectionPlaceholder() {
            PlaceholderContext ctx = PlaceholderContext.builder()
                    .direction("SEND")
                    .build();

            String result = service.resolvePath("/data/${direction}/archive", ctx);
            assertThat(result).isEqualTo("/data/SEND/archive");
        }

        @Test
        @DisplayName("should resolve timestamp placeholder")
        void shouldResolveTimestampPlaceholder() {
            String result = service.resolvePath("/data/file_${timestamp}.dat",
                    PlaceholderContext.builder().build());

            // Timestamp format: yyyyMMdd_HHmmss
            assertThat(result).matches("/data/file_\\d{8}_\\d{6}\\.dat");
        }

        @Test
        @DisplayName("should resolve date placeholder")
        void shouldResolveDatePlaceholder() {
            String result = service.resolvePath("/data/${date}/files",
                    PlaceholderContext.builder().build());

            // Date format: yyyyMMdd
            assertThat(result).matches("/data/\\d{8}/files");
        }

        @Test
        @DisplayName("should resolve time placeholder")
        void shouldResolveTimePlaceholder() {
            String result = service.resolvePath("/data/file_${time}.dat",
                    PlaceholderContext.builder().build());

            // Time format: HHmmss
            assertThat(result).matches("/data/file_\\d{6}\\.dat");
        }

        @Test
        @DisplayName("should resolve year placeholder")
        void shouldResolveYearPlaceholder() {
            String result = service.resolvePath("/data/${year}/archive",
                    PlaceholderContext.builder().build());

            int currentYear = LocalDateTime.now().getYear();
            assertThat(result).isEqualTo("/data/" + currentYear + "/archive");
        }

        @Test
        @DisplayName("should resolve month placeholder with zero padding")
        void shouldResolveMonthPlaceholder() {
            String result = service.resolvePath("/data/${month}/files",
                    PlaceholderContext.builder().build());

            // Month format: 01-12
            assertThat(result).matches("/data/\\d{2}/files");
        }

        @Test
        @DisplayName("should resolve day placeholder with zero padding")
        void shouldResolveDayPlaceholder() {
            String result = service.resolvePath("/data/${day}/files",
                    PlaceholderContext.builder().build());

            // Day format: 01-31
            assertThat(result).matches("/data/\\d{2}/files");
        }

        @Test
        @DisplayName("should resolve hour placeholder with zero padding")
        void shouldResolveHourPlaceholder() {
            String result = service.resolvePath("/data/${hour}/files",
                    PlaceholderContext.builder().build());

            // Hour format: 00-23
            assertThat(result).matches("/data/\\d{2}/files");
        }

        @Test
        @DisplayName("should resolve minute placeholder with zero padding")
        void shouldResolveMinutePlaceholder() {
            String result = service.resolvePath("/data/${minute}/files",
                    PlaceholderContext.builder().build());

            assertThat(result).matches("/data/\\d{2}/files");
        }

        @Test
        @DisplayName("should resolve second placeholder with zero padding")
        void shouldResolveSecondPlaceholder() {
            String result = service.resolvePath("/data/${second}/files",
                    PlaceholderContext.builder().build());

            assertThat(result).matches("/data/\\d{2}/files");
        }

        @Test
        @DisplayName("should resolve uuid placeholder")
        void shouldResolveUuidPlaceholder() {
            String result = service.resolvePath("/data/${uuid}.dat",
                    PlaceholderContext.builder().build());

            // UUID format
            assertThat(result).matches("/data/[a-f0-9-]{36}\\.dat");
        }

        @Test
        @DisplayName("should resolve multiple placeholders")
        void shouldResolveMultiplePlaceholders() {
            PlaceholderContext ctx = PlaceholderContext.builder()
                    .partnerId("CLIENT001")
                    .serverId("SRV001")
                    .direction("RECEIVE")
                    .build();

            String result = service.resolvePath(
                    "/data/${partner}/${server}/${direction}/files", ctx);
            assertThat(result).isEqualTo("/data/CLIENT001/SRV001/RECEIVE/files");
        }

        @Test
        @DisplayName("should keep unresolved placeholders")
        void shouldKeepUnresolvedPlaceholders() {
            String result = service.resolvePath("/data/${unknown}/files",
                    PlaceholderContext.builder().build());

            assertThat(result).isEqualTo("/data/${unknown}/files");
        }

        @Test
        @DisplayName("should handle placeholder case insensitively")
        void shouldHandlePlaceholderCaseInsensitively() {
            PlaceholderContext ctx = PlaceholderContext.builder()
                    .partnerId("CLIENT001")
                    .build();

            String result = service.resolvePath("/data/${PARTNER}/files", ctx);
            assertThat(result).isEqualTo("/data/CLIENT001/files");
        }

        @Test
        @DisplayName("should handle special regex characters in replacement")
        void shouldHandleSpecialRegexCharacters() {
            PlaceholderContext ctx = PlaceholderContext.builder()
                    .partnerId("CLIENT$01")
                    .build();

            String result = service.resolvePath("/data/${partner}/files", ctx);
            assertThat(result).isEqualTo("/data/CLIENT$01/files");
        }
    }

    @Nested
    @DisplayName("getAvailablePlaceholders")
    class GetAvailablePlaceholdersTests {

        @Test
        @DisplayName("should return all available placeholders")
        void shouldReturnAllAvailablePlaceholders() {
            Map<String, String> placeholders = PathPlaceholderService.getAvailablePlaceholders();

            assertThat(placeholders).containsKeys(
                    "${partner}",
                    "${virtualFile}",
                    "${server}",
                    "${serverName}",
                    "${timestamp}",
                    "${date}",
                    "${time}",
                    "${year}",
                    "${month}",
                    "${day}",
                    "${hour}",
                    "${minute}",
                    "${second}",
                    "${uuid}",
                    "${direction}");
        }

        @Test
        @DisplayName("should include descriptions for all placeholders")
        void shouldIncludeDescriptions() {
            Map<String, String> placeholders = PathPlaceholderService.getAvailablePlaceholders();

            assertThat(placeholders.get("${partner}")).isNotBlank();
            assertThat(placeholders.get("${timestamp}")).isNotBlank();
            assertThat(placeholders.get("${uuid}")).isNotBlank();
        }
    }

    @Nested
    @DisplayName("PlaceholderContext")
    class PlaceholderContextTests {

        @Test
        @DisplayName("should build context with all fields")
        void shouldBuildContextWithAllFields() {
            PlaceholderContext ctx = PlaceholderContext.builder()
                    .partnerId("PARTNER1")
                    .virtualFile("VFILE1")
                    .serverId("SERVER1")
                    .serverName("Production")
                    .direction("SEND")
                    .build();

            assertThat(ctx.getPartnerId()).isEqualTo("PARTNER1");
            assertThat(ctx.getVirtualFile()).isEqualTo("VFILE1");
            assertThat(ctx.getServerId()).isEqualTo("SERVER1");
            assertThat(ctx.getServerName()).isEqualTo("Production");
            assertThat(ctx.getDirection()).isEqualTo("SEND");
        }

        @Test
        @DisplayName("should build empty context")
        void shouldBuildEmptyContext() {
            PlaceholderContext ctx = PlaceholderContext.builder().build();

            assertThat(ctx.getPartnerId()).isNull();
            assertThat(ctx.getVirtualFile()).isNull();
            assertThat(ctx.getServerId()).isNull();
            assertThat(ctx.getServerName()).isNull();
            assertThat(ctx.getDirection()).isNull();
        }
    }
}
