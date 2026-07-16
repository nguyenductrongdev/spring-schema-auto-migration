package io.github.nguyenductrongdev.automigration.cassandra;

import org.springframework.boot.SpringBootVersion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SpringBootCompatibility {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\..*)?$");
    private static final String SUPPORTED_VERSIONS = "3.5.x, 4.0.x, and 4.1.x";

    private SpringBootCompatibility() {
    }

    static void verifySupported() {
        verifySupported(SpringBootVersion.getVersion());
    }

    static void verifySupported(String version) {
        Matcher matcher = version == null ? null : VERSION_PATTERN.matcher(version);
        if (matcher == null || !matcher.matches()) {
            throw unsupported(version);
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        boolean supported = major == 3 && minor == 5
                || major == 4 && (minor == 0 || minor == 1);
        if (!supported) {
            throw unsupported(version);
        }
    }

    private static IllegalStateException unsupported(String version) {
        return new IllegalStateException(
                "Unsupported Spring Boot version '" + String.valueOf(version)
                        + "'. This release supports " + SUPPORTED_VERSIONS);
    }
}