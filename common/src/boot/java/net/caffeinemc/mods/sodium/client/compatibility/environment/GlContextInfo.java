package net.caffeinemc.mods.sodium.client.compatibility.environment;

import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL46C;

import java.util.Objects;

public record GlContextInfo(String vendor, String renderer, String version) {
    public static GlContextInfo create() {
        String vendor = Objects.requireNonNull(GL46C.glGetString(GL46C.GL_VENDOR),
                "GL_VENDOR is NULL");
        String renderer = Objects.requireNonNull(GL46C.glGetString(GL46C.GL_RENDERER),
                "GL_RENDERER is NULL");
        String version = Objects.requireNonNull(GL46C.glGetString(GL46C.GL_VERSION),
                "GL_VERSION is NULL");

        return new GlContextInfo(vendor, renderer, version);
    }
}
