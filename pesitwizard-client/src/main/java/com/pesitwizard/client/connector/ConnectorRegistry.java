package com.pesitwizard.client.connector;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.pesitwizard.connector.ConnectorException;
import com.pesitwizard.connector.ConnectorFactory;
import com.pesitwizard.connector.StorageConnector;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Registry for storage connectors.
 * Discovers and manages connector plugins via Java ServiceLoader.
 * Supports hot-reload of connectors from the connectors/ directory.
 */
@Slf4j
@Component
public class ConnectorRegistry {

    @Value("${pesitwizard.connectors.directory:connectors}")
    private String connectorsDirectory;

    @Value("${pesitwizard.connectors.hot-reload:true}")
    private boolean hotReloadEnabled;

    private final Map<String, ConnectorFactory> factories = new ConcurrentHashMap<>();
    private final Map<String, StorageConnector> instances = new ConcurrentHashMap<>();
    private volatile URLClassLoader pluginClassLoader;
    private volatile long lastScanTime = 0;

    @PostConstruct
    public void init() {
        log.info("Initializing connector registry, directory: {}", connectorsDirectory);
        loadConnectors();
        log.info("Connector registry initialized with {} type(s): {}",
                factories.size(), factories.keySet());
    }

    /**
     * Load all connectors (built-in + external)
     */
    private void loadConnectors() {
        // Load built-in connectors from classpath
        ServiceLoader<ConnectorFactory> builtIn = ServiceLoader.load(ConnectorFactory.class);
        for (ConnectorFactory factory : builtIn) {
            registerFactory(factory);
        }

        // Load external connectors from directory
        loadExternalConnectors();
    }

    /**
     * Load external connectors from connectors/ directory
     */
    private void loadExternalConnectors() {
        Path connectorsPath = Path.of(connectorsDirectory);
        if (!Files.exists(connectorsPath)) {
            log.debug("Connectors directory not found: {}", connectorsPath.toAbsolutePath());
            try {
                Files.createDirectories(connectorsPath);
                log.info("Created connectors directory: {}", connectorsPath.toAbsolutePath());
            } catch (IOException e) {
                log.warn("Could not create connectors directory: {}", e.getMessage());
            }
            return;
        }

        try (Stream<Path> jars = Files.list(connectorsPath)
                .filter(p -> p.toString().endsWith(".jar"))) {

            URL[] urls = jars.map(this::toUrl).filter(u -> u != null).toArray(URL[]::new);

            if (urls.length == 0) {
                log.debug("No connector JARs found in {}", connectorsPath);
                return;
            }

            log.info("Loading {} connector JAR(s) from {}", urls.length, connectorsPath);

            // Close previous classloader if exists
            closePluginClassLoader();

            pluginClassLoader = new URLClassLoader(urls, getClass().getClassLoader());
            ServiceLoader<ConnectorFactory> loader = ServiceLoader.load(
                    ConnectorFactory.class, pluginClassLoader);

            for (ConnectorFactory factory : loader) {
                registerFactory(factory);
            }

            lastScanTime = System.currentTimeMillis();

        } catch (IOException e) {
            log.error("Failed to load external connectors: {}", e.getMessage(), e);
        }
    }

    private URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (java.net.MalformedURLException e) {
            log.warn("Invalid connector path: {}", path);
            return null;
        }
    }

    /**
     * Register a connector factory
     */
    public void registerFactory(ConnectorFactory factory) {
        String type = factory.getType();
        factories.put(type, factory);
        log.info("Registered connector: {} v{} ({})",
                factory.getName(), factory.getVersion(), type);
    }

    /**
     * Get all available connector types
     */
    public Collection<String> getAvailableTypes() {
        return factories.keySet();
    }

    /**
     * Get connector factory by type
     */
    public ConnectorFactory getFactory(String type) {
        return factories.get(type);
    }

    /**
     * Create a new connector instance
     */
    public StorageConnector createConnector(String type, Map<String, String> config)
            throws ConnectorException {

        ConnectorFactory factory = factories.get(type);
        if (factory == null) {
            throw new ConnectorException(
                    ConnectorException.ErrorCode.INVALID_CONFIG,
                    "Unknown connector type: " + type + ". Available: " + factories.keySet());
        }

        StorageConnector connector = factory.create();
        connector.initialize(config);
        return connector;
    }

    /**
     * Create and register a named connector instance
     */
    public StorageConnector createAndRegister(String name, String type, Map<String, String> config)
            throws ConnectorException {

        StorageConnector connector = createConnector(type, config);

        // Close existing connector with same name
        StorageConnector existing = instances.put(name, connector);
        if (existing != null) {
            try {
                existing.close();
            } catch (RuntimeException e) {
                log.warn("Error closing existing connector {}: {}", name, e.getMessage());
            }
        }

        log.info("Registered connector instance: {} (type: {})", name, type);
        return connector;
    }

    /**
     * Get a registered connector instance by name
     */
    public StorageConnector getConnector(String name) {
        return instances.get(name);
    }

    /**
     * Get all registered connector instance names
     */
    public Collection<String> getRegisteredConnectors() {
        return instances.keySet();
    }

    /**
     * Remove a connector instance
     */
    public void removeConnector(String name) {
        StorageConnector connector = instances.remove(name);
        if (connector != null) {
            try {
                connector.close();
                log.info("Removed connector instance: {}", name);
            } catch (RuntimeException e) {
                log.warn("Error closing connector {}: {}", name, e.getMessage());
            }
        }
    }

    /**
     * Manually reload all connectors from directory
     */
    public void reloadConnectors() {
        log.info("Reloading connectors...");
        loadExternalConnectors();
    }

    /**
     * Hot-reload connectors from directory (scheduled task)
     */
    @Scheduled(fixedDelayString = "${pesitwizard.connectors.reload-interval:60000}")
    public void checkForNewConnectors() {
        if (!hotReloadEnabled) {
            return;
        }

        Path connectorsPath = Path.of(connectorsDirectory);
        if (!Files.exists(connectorsPath)) {
            return;
        }

        try {
            // Check if any JAR is newer than last scan
            boolean hasNewJars = Files.list(connectorsPath)
                    .filter(p -> p.toString().endsWith(".jar"))
                    .anyMatch(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis() > lastScanTime;
                        } catch (IOException e) {
                            return false;
                        }
                    });

            if (hasNewJars) {
                log.info("New connector JARs detected, reloading...");
                loadExternalConnectors();
            }

        } catch (IOException e) {
            log.debug("Error scanning connectors directory: {}", e.getMessage());
        }
    }

    private void closePluginClassLoader() {
        if (pluginClassLoader != null) {
            try {
                pluginClassLoader.close();
            } catch (IOException e) {
                log.debug("Error closing plugin classloader: {}", e.getMessage());
            }
            pluginClassLoader = null;
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down connector registry");

        // Close all connector instances
        for (String name : instances.keySet()) {
            removeConnector(name);
        }

        closePluginClassLoader();
    }
}
