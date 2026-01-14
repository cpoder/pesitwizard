package com.pesitwizard.server.util;

import java.util.regex.Pattern;

/**
 * Validator for PeSIT protocol identifiers.
 *
 * PeSIT is a legacy protocol with strict identifier requirements:
 * - Maximum 8 characters
 * - Uppercase letters A-Z and digits 0-9 only
 * - No special characters, spaces, or underscores
 *
 * These rules apply to:
 * - Server identifiers (PI_04_SERVEUR)
 * - Partner identifiers (PI_03_DEMANDEUR)
 * - File identifiers (PI_12_NOM_FICHIER)
 */
public class PesitIdValidator {

    private static final int MAX_LENGTH = 8;
    private static final Pattern VALID_PATTERN = Pattern.compile("^[A-Z0-9]{1,8}$");

    /**
     * Validate a PeSIT identifier.
     *
     * @param id the identifier to validate
     * @param type the type of identifier (for error messages)
     * @return null if valid, error message if invalid
     */
    public static String validate(String id, String type) {
        if (id == null || id.isEmpty()) {
            return type + " ID cannot be empty";
        }

        if (id.length() > MAX_LENGTH) {
            return type + " ID must be " + MAX_LENGTH + " characters or less (got " + id.length() + " chars): " + id;
        }

        if (!VALID_PATTERN.matcher(id).matches()) {
            return type + " ID must contain only uppercase letters (A-Z) and digits (0-9), no special characters or underscores: " + id;
        }

        return null;
    }

    /**
     * Check if an identifier is valid.
     *
     * @param id the identifier to check
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String id) {
        return id != null && !id.isEmpty() && VALID_PATTERN.matcher(id).matches();
    }

    /**
     * Validate and throw exception if invalid.
     *
     * @param id the identifier to validate
     * @param type the type of identifier
     * @throws IllegalArgumentException if invalid
     */
    public static void validateOrThrow(String id, String type) {
        String error = validate(id, type);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }
}
