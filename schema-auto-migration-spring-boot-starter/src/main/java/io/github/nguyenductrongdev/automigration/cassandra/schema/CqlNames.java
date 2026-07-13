package io.github.nguyenductrongdev.automigration.cassandra.schema;

import java.util.Locale;

/** Cassandra identifier helpers used by scanners and CQL generation. */
public final class CqlNames {

    private CqlNames() {
    }

    public static String internal(String cqlName) {
        String value = cqlName.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\"\"", "\"");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    public static String quote(String internalName) {
        return "\"" + internalName.replace("\"", "\"\"") + "\"";
    }

    public static String qualified(String keyspace, String objectName) {
        return quote(keyspace) + "." + quote(objectName);
    }

    public static String snakeCase(String value) {
        return value
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replace('-', '_')
                .toLowerCase(Locale.ROOT);
    }
}

