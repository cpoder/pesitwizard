package com.pesitwizard.client.service;

/**
 * Exception thrown when a PeSIT transfer receives IDT with PI 19 = 4, indicating an error that
 * requires automatic restart from the last sync point.
 */
public class RestartRequiredException extends Exception {
    private final int syncPoint;
    private final long bytePosition;
    private final long bytesTransferred;

    public RestartRequiredException(int syncPoint, long bytePosition, long bytesTransferred) {
        super(
                "Restart required from sync point "
                        + syncPoint
                        + " at byte position "
                        + bytePosition);
        this.syncPoint = syncPoint;
        this.bytePosition = bytePosition;
        this.bytesTransferred = bytesTransferred;
    }

    public int getSyncPoint() {
        return syncPoint;
    }

    public long getBytePosition() {
        return bytePosition;
    }

    public long getBytesTransferred() {
        return bytesTransferred;
    }
}
