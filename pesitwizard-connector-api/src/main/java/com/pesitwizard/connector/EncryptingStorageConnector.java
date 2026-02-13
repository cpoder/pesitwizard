package com.pesitwizard.connector;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Decorator that adds AES-256-GCM at-rest encryption to any StorageConnector.
 *
 * <p>Encrypted file format: [12-byte IV][AES-GCM ciphertext + 16-byte auth tag]
 *
 * <p>The encryption key is supplied externally (typically derived from the master key via the
 * SecretsService). Each file gets a unique random IV.
 */
public class EncryptingStorageConnector implements StorageConnector {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StorageConnector delegate;
    private final SecretKey encryptionKey;

    public EncryptingStorageConnector(StorageConnector delegate, SecretKey encryptionKey) {
        this.delegate = delegate;
        this.encryptionKey = encryptionKey;
    }

    @Override
    public InputStream read(String path) throws ConnectorException {
        return read(path, 0);
    }

    @Override
    public InputStream read(String path, long offset) throws ConnectorException {
        try {
            InputStream raw = delegate.read(path);

            // Read the IV from the file header
            byte[] iv = new byte[GCM_IV_LENGTH];
            int bytesRead = 0;
            while (bytesRead < GCM_IV_LENGTH) {
                int n = raw.read(iv, bytesRead, GCM_IV_LENGTH - bytesRead);
                if (n < 0) {
                    throw new ConnectorException(
                            ConnectorException.ErrorCode.UNKNOWN,
                            "Encrypted file too short - missing IV header");
                }
                bytesRead += n;
            }

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            CipherInputStream cis = new CipherInputStream(raw, cipher);

            // Skip decrypted bytes if offset > 0 (for resume support)
            if (offset > 0) {
                cis.skipNBytes(offset);
            }

            return cis;
        } catch (GeneralSecurityException e) {
            throw new ConnectorException(
                    ConnectorException.ErrorCode.UNKNOWN,
                    "Decryption initialization failed: " + e.getMessage(),
                    e);
        } catch (IOException e) {
            throw new ConnectorException(
                    ConnectorException.ErrorCode.UNKNOWN,
                    "Failed to read encrypted file header: " + e.getMessage(),
                    e);
        }
    }

    @Override
    public OutputStream write(String path) throws ConnectorException {
        return write(path, false);
    }

    @Override
    public OutputStream write(String path, boolean append) throws ConnectorException {
        if (append) {
            // GCM doesn't support appending to encrypted streams - the auth tag
            // is at the end. For resume, the entire file must be re-encrypted.
            throw new ConnectorException(
                    ConnectorException.ErrorCode.NOT_SUPPORTED,
                    "Append mode not supported with at-rest encryption. Use full re-write instead.");
        }

        try {
            OutputStream raw = delegate.write(path, false);

            // Generate random IV and write it as file header
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            raw.write(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

            // Wrap in CipherOutputStream. On close, it finalizes the GCM tag.
            return new FlushSafeCipherOutputStream(raw, cipher);
        } catch (GeneralSecurityException e) {
            throw new ConnectorException(
                    ConnectorException.ErrorCode.NOT_SUPPORTED,
                    "Encryption initialization failed: " + e.getMessage(),
                    e);
        } catch (IOException e) {
            throw new ConnectorException(
                    ConnectorException.ErrorCode.NOT_SUPPORTED,
                    "Failed to write encrypted file header: " + e.getMessage(),
                    e);
        }
    }

    @Override
    public FileMetadata getMetadata(String path) throws ConnectorException {
        FileMetadata meta = delegate.getMetadata(path);
        // Adjust reported size to subtract IV overhead
        if (meta.getSize() > GCM_IV_LENGTH) {
            meta.setSize(meta.getSize() - GCM_IV_LENGTH - 16); // -IV -authTag
        }
        return meta;
    }

    // ========== Delegated methods ==========

    @Override
    public String getType() {
        return delegate.getType();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getVersion() {
        return delegate.getVersion();
    }

    @Override
    public void initialize(Map<String, String> config) throws ConnectorException {
        delegate.initialize(config);
    }

    @Override
    public boolean testConnection() throws ConnectorException {
        return delegate.testConnection();
    }

    @Override
    public boolean exists(String path) throws ConnectorException {
        return delegate.exists(path);
    }

    @Override
    public List<FileMetadata> list(String path) throws ConnectorException {
        return delegate.list(path);
    }

    @Override
    public void delete(String path) throws ConnectorException {
        delegate.delete(path);
    }

    @Override
    public void mkdir(String path) throws ConnectorException {
        delegate.mkdir(path);
    }

    @Override
    public void rename(String s, String t) throws ConnectorException {
        delegate.rename(s, t);
    }

    @Override
    public List<ConfigParameter> getRequiredParameters() {
        return delegate.getRequiredParameters();
    }

    @Override
    public List<ConfigParameter> getOptionalParameters() {
        return delegate.getOptionalParameters();
    }

    @Override
    public boolean supportsResume() {
        return false;
    } // Resume not supported with GCM

    @Override
    public void close() {
        delegate.close();
    }

    /**
     * CipherOutputStream wrapper that properly closes the underlying stream. Standard
     * CipherOutputStream.close() calls doFinal() but may not flush the underlying stream before
     * closing.
     */
    private static class FlushSafeCipherOutputStream extends FilterOutputStream {
        private final CipherOutputStream cos;

        FlushSafeCipherOutputStream(OutputStream out, Cipher cipher) {
            super(new CipherOutputStream(out, cipher));
            this.cos = (CipherOutputStream) this.out;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            cos.write(b, off, len);
        }

        @Override
        public void write(int b) throws IOException {
            cos.write(b);
        }

        @Override
        public void close() throws IOException {
            cos.close(); // doFinal + flush + close underlying
        }
    }
}
