package net.caffeinemc.mods.sodium.client.gl.tessellation;

import org.lwjgl.opengl.GL46C;
import org.lwjgl.opengl.GL40C;

public enum GlPrimitiveType {
    POINTS(GL46C.GL_POINTS),
    LINES(GL46C.GL_LINES),
    TRIANGLES(GL46C.GL_TRIANGLES),
    PATCHES(GL40C.GL_PATCHES);

    private final int id;

    GlPrimitiveType(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }
}
