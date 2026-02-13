package com.pesitwizard.transport;

/**
 * Supported transport types for PESIT protocol Based on binary analysis showing TCP, SSL, and X.25
 * support
 */
public enum TransportType {
    /** Plain TCP/IP connection */
    TCP("TCP", false),

    /** SSL/TLS over TCP/IP */
    SSL("SSL", true),

    /** X.25 packet-switched network (legacy) */
    X25("X25", false);

    private final String name;
    private final boolean secure;

    TransportType(String name, boolean secure) {
        this.name = name;
        this.secure = secure;
    }

    public String getName() {
        return name;
    }

    public boolean isSecure() {
        return secure;
    }
}
