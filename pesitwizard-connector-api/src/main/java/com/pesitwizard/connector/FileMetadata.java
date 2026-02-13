package com.pesitwizard.connector;

import java.time.Instant;
import java.util.Map;

/** File metadata returned by storage connectors. */
public class FileMetadata {

    private String name;
    private String path;
    private long size;
    private Instant lastModified;
    private Instant createdAt;
    private boolean directory;
    private String contentType;
    private String checksum;
    private String checksumAlgorithm;
    private Map<String, String> attributes;

    public FileMetadata() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isDirectory() {
        return directory;
    }

    public void setDirectory(boolean directory) {
        this.directory = directory;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getChecksumAlgorithm() {
        return checksumAlgorithm;
    }

    public void setChecksumAlgorithm(String checksumAlgorithm) {
        this.checksumAlgorithm = checksumAlgorithm;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final FileMetadata meta = new FileMetadata();

        public Builder name(String name) {
            meta.name = name;
            return this;
        }

        public Builder path(String path) {
            meta.path = path;
            return this;
        }

        public Builder size(long size) {
            meta.size = size;
            return this;
        }

        public Builder lastModified(Instant lastModified) {
            meta.lastModified = lastModified;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            meta.createdAt = createdAt;
            return this;
        }

        public Builder directory(boolean directory) {
            meta.directory = directory;
            return this;
        }

        public Builder contentType(String contentType) {
            meta.contentType = contentType;
            return this;
        }

        public Builder checksum(String checksum) {
            meta.checksum = checksum;
            return this;
        }

        public Builder checksumAlgorithm(String alg) {
            meta.checksumAlgorithm = alg;
            return this;
        }

        public Builder attributes(Map<String, String> attributes) {
            meta.attributes = attributes;
            return this;
        }

        public FileMetadata build() {
            return meta;
        }
    }
}
