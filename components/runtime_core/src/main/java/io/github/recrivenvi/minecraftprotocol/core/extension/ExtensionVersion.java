package io.github.recrivenvi.minecraftprotocol.core.extension;

import java.math.BigInteger;

/** Exact upstream identities or dotted-numeric intervals; qualifiers require exact identities. */
final class ExtensionVersion {
    private ExtensionVersion() {}

    static boolean accepts(String requirement, String actual) {
        if (requirement == null || requirement.isBlank())
            throw ExtensionManifest.invalid("Dependency version required");
        if (!requirement.startsWith("[") && !requirement.startsWith("(")) {
            if (!requirement.matches("[A-Za-z0-9_.+~-]{1,128}"))
                throw ExtensionManifest.invalid("Unsupported dependency version grammar");
            return requirement.equals(actual);
        }
        if (!(requirement.endsWith("]") || requirement.endsWith(")")))
            throw ExtensionManifest.invalid("Malformed version interval");
        String[] bounds = requirement.substring(1, requirement.length() - 1).split(",", -1);
        if (bounds.length != 2 || bounds[0].isBlank() && bounds[1].isBlank())
            throw ExtensionManifest.invalid("A bounded version interval is required");
        for (String bound : bounds)
            if (!bound.isEmpty() && !bound.matches("[0-9]+(?:\\.[0-9]+)*"))
                throw ExtensionManifest.invalid("Version intervals require dotted numeric bounds");
        if (!bounds[0].isEmpty()
                && !bounds[1].isEmpty()
                && (compare(bounds[0], bounds[1]) > 0
                        || compare(bounds[0], bounds[1]) == 0
                                && !(requirement.startsWith("[") && requirement.endsWith("]"))))
            throw ExtensionManifest.invalid("Empty or reversed version interval");
        if (!actual.matches("[0-9]+(?:\\.[0-9]+)*")) return false;
        return (bounds[0].isEmpty()
                        || compare(actual, bounds[0]) > 0
                        || requirement.startsWith("[") && compare(actual, bounds[0]) == 0)
                && (bounds[1].isEmpty()
                        || compare(actual, bounds[1]) < 0
                        || requirement.endsWith("]") && compare(actual, bounds[1]) == 0);
    }

    private static int compare(String left, String right) {
        String[] a = left.split("[._+~-]"), b = right.split("[._+~-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            String x = i < a.length ? a[i] : "0", y = i < b.length ? b[i] : "0";
            int difference =
                    x.matches("[0-9]+") && y.matches("[0-9]+")
                            ? new BigInteger(x).compareTo(new BigInteger(y))
                            : x.compareToIgnoreCase(y);
            if (difference != 0) return difference;
        }
        return 0;
    }
}
