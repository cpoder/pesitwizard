package com.pesitwizard.transport;

import java.io.IOException;
import java.net.Socket;

/**
 * TCP/IP transport implementation for PESIT protocol.
 * Plain TCP without encryption.
 */
public class TcpTransportChannel extends AbstractSocketTransportChannel {

    public TcpTransportChannel(String host, int port) {
        super(host, port);
    }

    @Override
    protected Socket createSocket() throws IOException {
        return new Socket(host, port);
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public TransportType getTransportType() {
        return TransportType.TCP;
    }
}
