package com.pesitwizard.security;

/**
 * Interface for secret management providers.
 * Supports different backends: local AES encryption, HashiCorp Vault, etc.
 */
public interface SecretsProvider {

    /**
     * Encrypt a secret value.
     * 
     * @param plaintext The plaintext value to encrypt
     * @return The encrypted value (format depends on provider)
     */
    String encrypt(String plaintext);

    /**
     * Encrypt a secret value with a contextual path for better organization.
     * For Vault, this creates a human-readable path like
     * "registry/github/password".
     * For AES, this delegates to encrypt() as context is not needed.
     * 
     * @param plaintext The plaintext value to encrypt
     * @param context   The context path (e.g., "registry/github/password")
     * @return The encrypted value (format depends on provider)
     */
    default String encrypt(String plaintext, String context) {
        return encrypt(plaintext);
    }

    /**
     * Decrypt a secret value.
     * 
     * @param ciphertext The encrypted value
     * @return The decrypted plaintext
     */
    String decrypt(String ciphertext);

    /**
     * Store a secret with a given key.
     * 
     * @param key   The secret key/path
     * @param value The secret value
     */
    void storeSecret(String key, String value);

    /**
     * Retrieve a secret by key.
     * 
     * @param key The secret key/path
     * @return The secret value, or null if not found
     */
    String getSecret(String key);

    /**
     * Delete a secret.
     * 
     * @param key The secret key/path
     */
    void deleteSecret(String key);

    /**
     * Check if the provider is properly configured and available.
     * 
     * @return true if the provider is ready to use
     */
    boolean isAvailable();

    /**
     * Get the provider type name.
     * 
     * @return Provider type (e.g., "AES", "VAULT", "NONE")
     */
    String getProviderType();
}
