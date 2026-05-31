package com.document.documentetl.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ChecksumUtils {

    private static final int BUFFER_SIZE = 8 * 1024;

    private ChecksumUtils() {
        // Utility class
    }

    public static String calculateSHA256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream input = Files.newInputStream(path);
                 DigestInputStream digestStream =
                         new DigestInputStream(new BufferedInputStream(input, BUFFER_SIZE), digest)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                while (digestStream.read(buffer) != -1) {
                    // Streaming read updates digest
                }
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new ChecksumCalculationException(
                    "Unable to calculate SHA-256 checksum for " + path + ": " + e.getMessage(), e);
        }
    }

    public static class ChecksumCalculationException extends RuntimeException {
        public ChecksumCalculationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
