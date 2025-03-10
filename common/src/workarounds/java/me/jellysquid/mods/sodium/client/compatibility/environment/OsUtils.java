package me.jellysquid.mods.sodium.client.compatibility.environment;

import org.apache.commons.lang3.SystemUtils;

import java.util.Locale;

public class OsUtils {
    private static final OperatingSystem OS = determineOs();

    public static OperatingSystem determineOs() {
        var name = System.getProperty("os.name");

        if (name != null) {
            var normalized = name.toLowerCase(Locale.ROOT);

            if (normalized.startsWith("windows")) {
                return OperatingSystem.WIN;
            } else if (normalized.startsWith("mac")) {
                return OperatingSystem.MAC;
            } else if (normalized.startsWith("linux")) {
                return OperatingSystem.LINUX;
            }
        }

        return OperatingSystem.UNKNOWN;
    }

    public static OperatingSystem getOs() {
        return OS;
    }

    public enum OperatingSystem {
        WIN,
        MAC,
        LINUX,
        UNKNOWN
    }
}