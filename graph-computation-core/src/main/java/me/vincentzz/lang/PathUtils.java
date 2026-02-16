package me.vincentzz.lang;

import java.nio.file.Path;

/**
 * Utility for consistent cross-platform path string conversion.
 * Always uses '/' as separator regardless of OS.
 */
public final class PathUtils {

    private PathUtils() {}

    /**
     * Convert a {@link Path} to a string using '/' as separator, even on Windows.
     */
    public static String toUnixString(Path path) {
        return path.toString().replace('\\', '/');
    }
}
