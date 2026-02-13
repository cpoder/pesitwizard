package com.pesitwizard.connector.s3;

import com.pesitwizard.connector.ConfigParameter;
import com.pesitwizard.connector.ConnectorException;
import com.pesitwizard.connector.FileMetadata;
import com.pesitwizard.connector.StorageConnector;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;

public class S3Connector implements StorageConnector {
    private static final Logger log = LoggerFactory.getLogger(S3Connector.class);
    private S3Client s3;
    private String bucket;
    private String prefix;
    private boolean initialized = false;

    @Override
    public String getType() {
        return "s3";
    }

    @Override
    public String getName() {
        return "AWS S3 / MinIO";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void initialize(Map<String, String> config) throws ConnectorException {
        bucket = config.get("bucket");
        prefix = config.getOrDefault("prefix", "");
        String accessKey = config.get("accessKey");
        String secretKey = config.get("secretKey");
        String region = config.getOrDefault("region", "us-east-1");
        String endpoint = config.get("endpoint");

        if (bucket == null)
            throw new ConnectorException(
                    ConnectorException.ErrorCode.INVALID_CONFIG, "Bucket required");

        try {
            S3ClientBuilder builder = S3Client.builder().region(Region.of(region));
            if (accessKey != null && secretKey != null) {
                builder.credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)));
            }
            if (endpoint != null) {
                builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
            }
            s3 = builder.build();
            initialized = true;
            log.info("S3 connected to bucket: {}", bucket);
        } catch (S3Exception | software.amazon.awssdk.core.exception.SdkClientException e) {
            throw new ConnectorException(
                    ConnectorException.ErrorCode.CONNECTION_FAILED, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ConnectorException(
                    ConnectorException.ErrorCode.INVALID_CONFIG, e.getMessage(), e);
        }
    }

    @Override
    public boolean testConnection() throws ConnectorException {
        checkInit();
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (S3Exception | software.amazon.awssdk.core.exception.SdkClientException e) {
            return false;
        }
    }

    @Override
    public boolean exists(String path) throws ConnectorException {
        checkInit();
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(resolve(path)).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public FileMetadata getMetadata(String path) throws ConnectorException {
        checkInit();
        try {
            HeadObjectResponse r =
                    s3.headObject(
                            HeadObjectRequest.builder().bucket(bucket).key(resolve(path)).build());
            return FileMetadata.builder()
                    .name(path)
                    .path(path)
                    .size(r.contentLength())
                    .lastModified(r.lastModified())
                    .directory(false)
                    .build();
        } catch (NoSuchKeyException e) {
            throw new ConnectorException(
                    ConnectorException.ErrorCode.FILE_NOT_FOUND, "Not found: " + path);
        }
    }

    @Override
    public List<FileMetadata> list(String path) throws ConnectorException {
        checkInit();
        String p = resolve(path);
        if (!p.isEmpty() && !p.endsWith("/")) p += "/";
        ListObjectsV2Request.Builder reqBuilder =
                ListObjectsV2Request.builder().bucket(bucket).prefix(p).delimiter("/");
        List<FileMetadata> result = new ArrayList<>();
        ListObjectsV2Response r;
        do {
            r = s3.listObjectsV2(reqBuilder.build());
            for (S3Object o : r.contents()) {
                String name = o.key().substring(p.length());
                if (!name.isEmpty())
                    result.add(
                            FileMetadata.builder()
                                    .name(name)
                                    .path(o.key())
                                    .size(o.size())
                                    .lastModified(o.lastModified())
                                    .directory(false)
                                    .build());
            }
            for (CommonPrefix cp : r.commonPrefixes()) {
                String name = cp.prefix().substring(p.length()).replace("/", "");
                result.add(
                        FileMetadata.builder()
                                .name(name)
                                .path(cp.prefix())
                                .directory(true)
                                .build());
            }
            reqBuilder.continuationToken(r.nextContinuationToken());
        } while (Boolean.TRUE.equals(r.isTruncated()));
        return result;
    }

    @Override
    public InputStream read(String path) throws ConnectorException {
        checkInit();
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(resolve(path)).build());
    }

    @Override
    public InputStream read(String path, long offset) throws ConnectorException {
        checkInit();
        return s3.getObject(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(resolve(path))
                        .range("bytes=" + offset + "-")
                        .build());
    }

    @Override
    public OutputStream write(String path) throws ConnectorException {
        return write(path, false);
    }

    @Override
    public OutputStream write(String path, boolean append) throws ConnectorException {
        checkInit();
        try {
            PipedInputStream pis = new PipedInputStream(65536);
            PipedOutputStream pos = new PipedOutputStream(pis);
            String key = resolve(path);
            java.util.concurrent.CompletableFuture<Void> uploadFuture =
                    new java.util.concurrent.CompletableFuture<>();
            Thread.startVirtualThread(
                    () -> {
                        try {
                            s3.putObject(
                                    PutObjectRequest.builder().bucket(bucket).key(key).build(),
                                    RequestBody.fromInputStream(pis, -1));
                            uploadFuture.complete(null);
                        } catch (S3Exception
                                | software.amazon.awssdk.core.exception.SdkClientException e) {
                            log.error("S3 upload error for key '{}': {}", key, e.getMessage(), e);
                            uploadFuture.completeExceptionally(e);
                            try {
                                pis.close();
                            } catch (java.io.IOException ignored) {
                            }
                        }
                    });
            return new S3ErrorPropagatingOutputStream(pos, uploadFuture, key);
        } catch (java.io.IOException e) {
            throw new ConnectorException("Write error", e);
        }
    }

    /**
     * OutputStream wrapper that checks the S3 upload future on close(), propagating any upload
     * errors back to the caller.
     */
    private static class S3ErrorPropagatingOutputStream extends OutputStream {
        private final PipedOutputStream delegate;
        private final java.util.concurrent.CompletableFuture<Void> uploadFuture;
        private final String key;

        S3ErrorPropagatingOutputStream(
                PipedOutputStream delegate,
                java.util.concurrent.CompletableFuture<Void> uploadFuture,
                String key) {
            this.delegate = delegate;
            this.uploadFuture = uploadFuture;
            this.key = key;
        }

        @Override
        public void write(int b) throws java.io.IOException {
            checkUploadError();
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws java.io.IOException {
            checkUploadError();
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws java.io.IOException {
            delegate.flush();
        }

        @Override
        public void close() throws java.io.IOException {
            delegate.close();
            try {
                uploadFuture.get(5, java.util.concurrent.TimeUnit.MINUTES);
            } catch (java.util.concurrent.ExecutionException e) {
                throw new java.io.IOException(
                        "S3 upload failed for key '" + key + "': " + e.getCause().getMessage(),
                        e.getCause());
            } catch (java.util.concurrent.TimeoutException e) {
                throw new java.io.IOException("S3 upload timed out for key '" + key + "'", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException("S3 upload interrupted for key '" + key + "'", e);
            }
        }

        private void checkUploadError() throws java.io.IOException {
            if (uploadFuture.isCompletedExceptionally()) {
                Throwable cause = uploadFuture.exceptionNow();
                throw new java.io.IOException(
                        "S3 upload failed for key '" + key + "': " + cause.getMessage(), cause);
            }
        }
    }

    @Override
    public void delete(String path) throws ConnectorException {
        checkInit();
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(resolve(path)).build());
    }

    @Override
    public void mkdir(String path) throws ConnectorException {
        /* S3 doesn't need explicit mkdir */
    }

    @Override
    public void rename(String src, String dst) throws ConnectorException {
        checkInit();
        s3.copyObject(
                CopyObjectRequest.builder()
                        .sourceBucket(bucket)
                        .sourceKey(resolve(src))
                        .destinationBucket(bucket)
                        .destinationKey(resolve(dst))
                        .build());
        delete(src);
    }

    @Override
    public List<ConfigParameter> getRequiredParameters() {
        return List.of(ConfigParameter.required("bucket", "S3 bucket name"));
    }

    @Override
    public List<ConfigParameter> getOptionalParameters() {
        return List.of(
                ConfigParameter.password("accessKey", "AWS Access Key"),
                ConfigParameter.password("secretKey", "AWS Secret Key"),
                ConfigParameter.optional("region", "AWS Region", "us-east-1"),
                ConfigParameter.optional("endpoint", "Custom endpoint (MinIO)", null));
    }

    @Override
    public boolean supportsResume() {
        return true;
    }

    @Override
    public void close() {
        if (s3 != null) s3.close();
        initialized = false;
    }

    private void checkInit() throws ConnectorException {
        if (!initialized)
            throw new ConnectorException(
                    ConnectorException.ErrorCode.INVALID_CONFIG, "Not initialized");
    }

    /** Resolve path and validate against path traversal (S3-17). */
    private String resolve(String p) throws ConnectorException {
        if (p == null || p.contains("..") || p.contains("\0")) {
            throw new ConnectorException(
                    ConnectorException.ErrorCode.INVALID_PATH,
                    "Invalid path: traversal or null bytes not allowed");
        }
        String resolved = prefix.isEmpty() ? p : prefix + "/" + p;
        // Verify resolved key stays within prefix
        if (!prefix.isEmpty() && !resolved.startsWith(prefix + "/") && !resolved.equals(prefix)) {
            throw new ConnectorException(
                    ConnectorException.ErrorCode.INVALID_PATH, "Path escapes prefix boundary");
        }
        return resolved;
    }
}
