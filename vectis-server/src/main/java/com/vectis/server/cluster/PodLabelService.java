package com.vectis.server.cluster;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that updates pod labels based on leader status.
 * This allows the PeSIT LoadBalancer service to route traffic only to the
 * leader pod.
 */
@Slf4j
@Service
public class PodLabelService implements ClusterEventListener {

    @Value("${pesit.cluster.enabled:false}")
    private boolean clusterEnabled;

    @Value("${POD_NAME:}")
    private String podName;

    @Value("${POD_NAMESPACE:default}")
    private String namespace;

    @Autowired
    private ClusterService clusterService;

    private KubernetesClient kubernetesClient;
    private boolean kubernetesAvailable = false;

    @PostConstruct
    public void init() {
        if (!clusterEnabled) {
            log.debug("Cluster mode disabled, pod label service not initialized");
            return;
        }

        if (podName == null || podName.isEmpty()) {
            log.warn("POD_NAME not set, pod label service disabled");
            return;
        }

        try {
            kubernetesClient = new KubernetesClientBuilder().build();
            kubernetesAvailable = true;
            clusterService.addListener(this);
            log.info("Pod label service initialized for pod {} in namespace {}", podName, namespace);

            // Check if we're already the leader (in case we missed the event)
            if (clusterService.isLeader()) {
                log.info("Already leader at init time, adding leader label");
                updateLeaderLabel(true);
            }
        } catch (Exception e) {
            log.warn("Could not initialize Kubernetes client: {}. Pod label updates disabled.", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (kubernetesClient != null) {
            // Remove leader label on shutdown
            updateLeaderLabel(false);
            kubernetesClient.close();
        }
    }

    @Override
    public void onClusterEvent(ClusterEvent event) {
        switch (event.getType()) {
            case BECAME_LEADER -> updateLeaderLabel(true);
            case LOST_LEADERSHIP -> updateLeaderLabel(false);
            default -> {
                /* ignore other events */ }
        }
    }

    private void updateLeaderLabel(boolean isLeader) {
        if (!kubernetesAvailable || podName == null || podName.isEmpty()) {
            return;
        }

        try {
            // Use JSON patch to update the label
            String patchJson;
            if (isLeader) {
                patchJson = "[{\"op\": \"add\", \"path\": \"/metadata/labels/pesit-leader\", \"value\": \"true\"}]";
            } else {
                patchJson = "[{\"op\": \"remove\", \"path\": \"/metadata/labels/pesit-leader\"}]";
            }

            kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .patch(PatchContext.of(PatchType.JSON), patchJson);

            if (isLeader) {
                log.info("Added leader label to pod {}", podName);
            } else {
                log.info("Removed leader label from pod {}", podName);
            }

        } catch (Exception e) {
            log.error("Failed to update leader label on pod {}: {}", podName, e.getMessage());
        }
    }
}
