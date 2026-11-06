package com.polycore.platform;

import java.util.ServiceLoader;

/**
 * Finds the {@link Platform} implementation of the current loader
 * (declared in META-INF/services/com.polycore.platform.Platform).
 */
public final class Services {
    public static final Platform PLATFORM = load();

    private Services() {}

    private static Platform load() {
        return ServiceLoader.load(Platform.class, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No Polycore platform implementation found"));
    }
}
