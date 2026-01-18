package com.pesitwizard.security;

import lombok.extern.slf4j.Slf4j;

/**
 * Composite secrets provider that enables transparent migration from AES to
 * Vault.
 * 
 * - New data is always encrypted with the primary provider (Vault when
 * available)
 * - Can decrypt both AES: and vault: prefixed data transparently
 * - When AES-encrypted data is re-saved, it gets migrated to Vault
 * automatically
 */
@Slf4j
public class CompositeSecretsProvider implements SecretsProvider {

    private final SecretsProvider primaryProvider;
    private final AesSecretsProvider aesProvider;

    public CompositeSecretsProvider(SecretsProvider primaryProvider, AesSecretsProvider aesProvider) {
        this.primaryProvider = primaryProvider;
        this.aesProvider = aesProvider;
        log.info("Composite secrets provider initialized: primary={}, fallback=AES",
                primaryProvider.getProviderType());
    }

    @Override
    public String encrypt(String plaintext) {
        // Always use primary provider for new encryption
        return primaryProvider.encrypt(plaintext);
    }

    @Override
    public String encrypt(String plaintext, String context) {
        // Always use primary provider for new encryption with context
        return primaryProvider.encrypt(plaintext, context);
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return ciphertext;
        }

        // Route to appropriate provider based on prefix
        if (ciphertext.startsWith("AES:")) {
            // Decrypt with AES provider
            log.debug("Decrypting AES-encrypted value");
            return aesProvider.decrypt(ciphertext);
        } else if (ciphertext.startsWith("vault:")) {
            // Decrypt with primary (Vault) provider
            log.debug("Decrypting Vault-stored value: {}",
                    ciphertext.substring(0, Math.min(20, ciphertext.length())) + "...");
            String decrypted = primaryProvider.decrypt(ciphertext);
            if (decrypted != null && decrypted.equals(ciphertext)) {
                log.warn("Vault decryption returned original reference - secret may not exist in Vault: {}",
                        ciphertext.substring(0, Math.min(20, ciphertext.length())) + "...");
            }
            return decrypted;
        }

        // Not encrypted, return as-is
        log.debug("Value not encrypted, returning as-is");
        return ciphertext;
    }

    @Override
    public void storeSecret(String key, String value) {
        primaryProvider.storeSecret(key, value);
    }

    @Override
    public String getSecret(String key) {
        return primaryProvider.getSecret(key);
    }

    @Override
    public void deleteSecret(String key) {
        primaryProvider.deleteSecret(key);
    }

    @Override
    public boolean isAvailable() {
        return primaryProvider.isAvailable();
    }

    @Override
    public String getProviderType() {
        return primaryProvider.getProviderType();
    }
}
