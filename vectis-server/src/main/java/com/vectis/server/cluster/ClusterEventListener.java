package com.vectis.server.cluster;

/**
 * Listener for cluster events
 */
@FunctionalInterface
public interface ClusterEventListener {

    /**
     * Called when a cluster event occurs
     * 
     * @param event the cluster event
     */
    void onClusterEvent(ClusterEvent event);
}
