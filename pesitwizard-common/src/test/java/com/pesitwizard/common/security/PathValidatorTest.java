package com.pesitwizard.common.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PathValidatorTest {

    @Test
    void validateFilename_withValidFilename_returnsFilename() {
        String result = PathValidator.validateFilename("test.txt");
        assertEquals("test.txt", result);
    }

    @Test
    void validateFilename_withPath_returnsOnlyFilename() {
        String result = PathValidator.validateFilename("/some/path/test.txt");
        assertEquals("test.txt", result);
    }

    @Test
    void validateFilename_withNull_throwsException() {
        assertThrows(SecurityException.class, () -> PathValidator.validateFilename(null));
    }

    @Test
    void validateFilename_withBlank_throwsException() {
        assertThrows(SecurityException.class, () -> PathValidator.validateFilename("   "));
    }

    @Test
    void validateFilename_withNullByte_throwsException() {
        assertThrows(SecurityException.class, () -> PathValidator.validateFilename("test\0.txt"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../etc/passwd",
            "..\\windows\\system32",
            "foo/../bar",
            "foo/..\\bar",
            "...../etc",
            "foo\0bar"
    })
    void containsTraversal_withTraversalAttempts_returnsTrue(String path) {
        assertTrue(PathValidator.containsTraversal(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "normal/path/file.txt",
            "file.txt",
            "some.file.with.dots.txt",
            "file-with-dashes.txt",
            "file_with_underscores.txt"
    })
    void containsTraversal_withNormalPaths_returnsFalse(String path) {
        assertFalse(PathValidator.containsTraversal(path));
    }

    @Test
    void isSafeFilename_withSafeFilename_returnsTrue() {
        assertTrue(PathValidator.isSafeFilename("test.txt"));
        assertTrue(PathValidator.isSafeFilename("file-name_123.dat"));
        assertTrue(PathValidator.isSafeFilename("My Document.pdf"));
    }

    @Test
    void isSafeFilename_withUnsafeFilename_returnsFalse() {
        assertFalse(PathValidator.isSafeFilename("test<script>.txt"));
        assertFalse(PathValidator.isSafeFilename("file|pipe.txt"));
        assertFalse(PathValidator.isSafeFilename("test;rm -rf.sh"));
    }

    @Test
    void sanitizeFilename_removesUnsafeCharacters() {
        assertEquals("test_script_.txt", PathValidator.sanitizeFilename("test<script>.txt"));
        assertEquals("file_pipe.txt", PathValidator.sanitizeFilename("file|pipe.txt"));
    }

    @Test
    void sanitizeFilename_removesLeadingDots() {
        assertEquals("hiddenfile", PathValidator.sanitizeFilename(".hiddenfile"));
        assertEquals("file", PathValidator.sanitizeFilename("...file"));
    }

    @Test
    void sanitizeFilename_truncatesLongFilename() {
        String longName = "a".repeat(300) + ".txt";
        String result = PathValidator.sanitizeFilename(longName);
        assertTrue(result.length() <= PathValidator.MAX_FILENAME_LENGTH);
        assertTrue(result.endsWith(".txt"));
    }

    @Test
    void sanitizeFilename_withNull_returnsUnnamed() {
        assertEquals("unnamed", PathValidator.sanitizeFilename(null));
    }

    @Test
    void sanitizeFilename_withBlank_returnsUnnamed() {
        assertEquals("unnamed", PathValidator.sanitizeFilename("   "));
    }

    @Test
    void validatePath_withNormalPath_succeeds() {
        // This just validates the path format, not existence
        var result = PathValidator.validatePath("/data/files/test.txt");
        assertNotNull(result);
    }

    @Test
    void validatePath_withTraversal_throwsException() {
        assertThrows(SecurityException.class, () ->
            PathValidator.validatePath("../../../etc/passwd"));
    }

    @Test
    void validatePath_withNull_throwsException() {
        assertThrows(SecurityException.class, () ->
            PathValidator.validatePath(null));
    }
}
