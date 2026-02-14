package com.pesitwizard.client.connector;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pesitwizard.connector.ConnectorException;
import com.pesitwizard.connector.ConnectorFactory;
import com.pesitwizard.connector.StorageConnector;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectorRegistryTest {

    private ConnectorRegistry registry;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        registry = new ConnectorRegistry();
        setField(registry, "connectorsDirectory", tempDir.toString());
        setField(registry, "hotReloadEnabled", false);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = ConnectorRegistry.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void registerFactory_andGetFactory() {
        ConnectorFactory factory = mockFactory("test", "Test Connector");
        registry.registerFactory(factory);

        assertThat(registry.getFactory("test")).isSameAs(factory);
    }

    @Test
    void getAvailableTypes_returnsRegistered() {
        registry.registerFactory(mockFactory("a", "A"));
        registry.registerFactory(mockFactory("b", "B"));

        assertThat(registry.getAvailableTypes()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void createConnector_unknownType_throws() {
        assertThatThrownBy(() -> registry.createConnector("unknown", Map.of()))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Unknown connector type");
    }

    @Test
    void createConnector_knownType_returnsInitialized() throws ConnectorException {
        StorageConnector mockConnector = mock(StorageConnector.class);
        ConnectorFactory factory = mockFactory("test", "Test");
        when(factory.create()).thenReturn(mockConnector);
        registry.registerFactory(factory);

        StorageConnector result = registry.createConnector("test", Map.of("key", "val"));
        assertThat(result).isSameAs(mockConnector);
        verify(mockConnector).initialize(Map.of("key", "val"));
    }

    @Test
    void createAndRegister_registersInstance() throws ConnectorException {
        StorageConnector mockConnector = mock(StorageConnector.class);
        ConnectorFactory factory = mockFactory("test", "Test");
        when(factory.create()).thenReturn(mockConnector);
        registry.registerFactory(factory);

        registry.createAndRegister("myconn", "test", Map.of());

        assertThat(registry.getConnector("myconn")).isSameAs(mockConnector);
        assertThat(registry.getRegisteredConnectors()).contains("myconn");
    }

    @Test
    void createAndRegister_replacesExisting_closesOld() throws ConnectorException {
        StorageConnector old = mock(StorageConnector.class);
        StorageConnector newer = mock(StorageConnector.class);
        ConnectorFactory factory = mockFactory("test", "Test");
        when(factory.create()).thenReturn(old).thenReturn(newer);
        registry.registerFactory(factory);

        registry.createAndRegister("myconn", "test", Map.of());
        registry.createAndRegister("myconn", "test", Map.of());

        assertThat(registry.getConnector("myconn")).isSameAs(newer);
        verify(old).close();
    }

    @Test
    void removeConnector_closesAndRemoves() throws ConnectorException {
        StorageConnector mockConnector = mock(StorageConnector.class);
        ConnectorFactory factory = mockFactory("test", "Test");
        when(factory.create()).thenReturn(mockConnector);
        registry.registerFactory(factory);

        registry.createAndRegister("myconn", "test", Map.of());
        registry.removeConnector("myconn");

        assertThat(registry.getConnector("myconn")).isNull();
        verify(mockConnector).close();
    }

    @Test
    void removeConnector_nonExistent_noOp() {
        assertThatCode(() -> registry.removeConnector("nope")).doesNotThrowAnyException();
    }

    @Test
    void shutdown_closesAllInstances() throws ConnectorException {
        StorageConnector c1 = mock(StorageConnector.class);
        StorageConnector c2 = mock(StorageConnector.class);
        ConnectorFactory factory = mockFactory("test", "Test");
        when(factory.create()).thenReturn(c1).thenReturn(c2);
        registry.registerFactory(factory);

        registry.createAndRegister("a", "test", Map.of());
        registry.createAndRegister("b", "test", Map.of());
        registry.shutdown();

        verify(c1).close();
        verify(c2).close();
    }

    @Test
    void init_loadsFromEmptyDirectory() {
        assertThatCode(() -> registry.init()).doesNotThrowAnyException();
    }

    private ConnectorFactory mockFactory(String type, String name) {
        ConnectorFactory f = mock(ConnectorFactory.class);
        when(f.getType()).thenReturn(type);
        when(f.getName()).thenReturn(name);
        when(f.getVersion()).thenReturn("1.0.0");
        return f;
    }
}
