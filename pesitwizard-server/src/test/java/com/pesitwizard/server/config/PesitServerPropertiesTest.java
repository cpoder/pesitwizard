package com.pesitwizard.server.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PesitServerProperties Tests")
class PesitServerPropertiesTest {

    private PesitServerProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PesitServerProperties();
    }

    @Test
    @DisplayName("should have default values")
    void shouldHaveDefaultValues() {
        assertEquals(5000, properties.getPort());
        assertEquals(5001, properties.getTlsPort());
        assertEquals("PESIT_SERVER", properties.getServerId());
        assertEquals(2, properties.getProtocolVersion());
        assertEquals(100, properties.getMaxConnections());
        assertEquals(30000, properties.getConnectionTimeout());
        assertEquals(60000, properties.getReadTimeout());
        assertEquals("/data/received", properties.getReceiveDirectory());
        assertEquals("/data/send", properties.getSendDirectory());
        assertEquals(4096, properties.getMaxEntitySize());
        assertTrue(properties.isSyncPointsEnabled());
        assertTrue(properties.isResyncEnabled());
        assertFalse(properties.isCrcEnabled());
        assertTrue(properties.isStrictPartnerCheck());
        assertFalse(properties.isStrictFileCheck());
    }

    @Test
    @DisplayName("getPartner should return null for null partnerId")
    void getPartnerShouldReturnNullForNullId() {
        assertNull(properties.getPartner(null));
    }

    @Test
    @DisplayName("getPartner should find exact match")
    void getPartnerShouldFindExactMatch() {
        PartnerConfig partner = new PartnerConfig();
        partner.setId("PARTNER1");
        properties.getPartners().put("PARTNER1", partner);

        assertNotNull(properties.getPartner("PARTNER1"));
        assertEquals("PARTNER1", properties.getPartner("PARTNER1").getId());
    }

    @Test
    @DisplayName("getPartner should find case-insensitive match by key")
    void getPartnerShouldFindCaseInsensitiveByKey() {
        PartnerConfig partner = new PartnerConfig();
        partner.setId("PARTNER2");
        properties.getPartners().put("PARTNER2", partner);

        assertNotNull(properties.getPartner("partner2"));
    }

    @Test
    @DisplayName("getPartner should find case-insensitive match by id")
    void getPartnerShouldFindCaseInsensitiveById() {
        PartnerConfig partner = new PartnerConfig();
        partner.setId("MyPartner");
        properties.getPartners().put("key", partner);

        assertNotNull(properties.getPartner("mypartner"));
    }

    @Test
    @DisplayName("getPartner should return null for unknown partner")
    void getPartnerShouldReturnNullForUnknown() {
        assertNull(properties.getPartner("UNKNOWN"));
    }

    @Test
    @DisplayName("getLogicalFile should return null for null filename")
    void getLogicalFileShouldReturnNullForNullFilename() {
        assertNull(properties.getLogicalFile(null));
    }

    @Test
    @DisplayName("getLogicalFile should find exact match")
    void getLogicalFileShouldFindExactMatch() {
        LogicalFileConfig file = LogicalFileConfig.builder()
                .id("FILE1")
                .build();
        properties.getFiles().put("FILE1", file);

        assertNotNull(properties.getLogicalFile("FILE1"));
    }

    @Test
    @DisplayName("getLogicalFile should find case-insensitive match by key")
    void getLogicalFileShouldFindCaseInsensitiveByKey() {
        LogicalFileConfig file = LogicalFileConfig.builder()
                .id("DATAFILE")
                .build();
        properties.getFiles().put("DATAFILE", file);

        assertNotNull(properties.getLogicalFile("datafile"));
    }

    @Test
    @DisplayName("getLogicalFile should find case-insensitive match by id")
    void getLogicalFileShouldFindCaseInsensitiveById() {
        LogicalFileConfig file = LogicalFileConfig.builder()
                .id("MyFile")
                .build();
        properties.getFiles().put("key", file);

        assertNotNull(properties.getLogicalFile("myfile"));
    }

    @Test
    @DisplayName("getLogicalFile should match pattern with wildcard")
    void getLogicalFileShouldMatchPattern() {
        LogicalFileConfig file = LogicalFileConfig.builder()
                .id("DATA_*")
                .build();
        properties.getFiles().put("DATA_*", file);

        assertNotNull(properties.getLogicalFile("DATA_001"));
        assertNotNull(properties.getLogicalFile("DATA_TEST"));
    }

    @Test
    @DisplayName("getLogicalFile should return null for non-matching pattern")
    void getLogicalFileShouldReturnNullForNonMatching() {
        LogicalFileConfig file = LogicalFileConfig.builder()
                .id("DATA_*")
                .build();
        properties.getFiles().put("DATA_*", file);

        assertNull(properties.getLogicalFile("OTHER_FILE"));
    }

    @Test
    @DisplayName("hasPartner should return correct result")
    void hasPartnerShouldReturnCorrectResult() {
        PartnerConfig partner = new PartnerConfig();
        partner.setId("EXIST");
        properties.getPartners().put("EXIST", partner);

        assertTrue(properties.hasPartner("EXIST"));
        assertFalse(properties.hasPartner("NOTEXIST"));
    }

    @Test
    @DisplayName("hasLogicalFile should return correct result")
    void hasLogicalFileShouldReturnCorrectResult() {
        LogicalFileConfig file = LogicalFileConfig.builder()
                .id("EXIST")
                .build();
        properties.getFiles().put("EXIST", file);

        assertTrue(properties.hasLogicalFile("EXIST"));
        assertFalse(properties.hasLogicalFile("NOTEXIST"));
    }

    @Test
    @DisplayName("should store custom values")
    void shouldStoreCustomValues() {
        properties.setPort(6000);
        properties.setTlsPort(6001);
        properties.setServerId("CUSTOM_SERVER");
        properties.setProtocolVersion(3);
        properties.setMaxConnections(50);
        properties.setConnectionTimeout(15000);
        properties.setReadTimeout(30000);
        properties.setReceiveDirectory("/custom/in");
        properties.setSendDirectory("/custom/out");
        properties.setMaxEntitySize(8192);
        properties.setSyncPointsEnabled(false);
        properties.setResyncEnabled(false);
        properties.setCrcEnabled(true);
        properties.setStrictPartnerCheck(false);
        properties.setStrictFileCheck(true);

        assertEquals(6000, properties.getPort());
        assertEquals(6001, properties.getTlsPort());
        assertEquals("CUSTOM_SERVER", properties.getServerId());
        assertEquals(3, properties.getProtocolVersion());
        assertEquals(50, properties.getMaxConnections());
        assertEquals(15000, properties.getConnectionTimeout());
        assertEquals(30000, properties.getReadTimeout());
        assertEquals("/custom/in", properties.getReceiveDirectory());
        assertEquals("/custom/out", properties.getSendDirectory());
        assertEquals(8192, properties.getMaxEntitySize());
        assertFalse(properties.isSyncPointsEnabled());
        assertFalse(properties.isResyncEnabled());
        assertTrue(properties.isCrcEnabled());
        assertFalse(properties.isStrictPartnerCheck());
        assertTrue(properties.isStrictFileCheck());
    }
}
