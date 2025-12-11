package net.caffeinemc.mods.sodium.client.gl.shader;

import org.lwjgl.opengl.GL46C;
import org.lwjgl.opengl.GL46C;

/**
 * An enumeration over the supported OpenGL shader types.
 */
public enum ShaderType {
    VERTEX(GL46C.GL_VERTEX_SHADER),
    GEOMETRY(GL46C.GL_GEOMETRY_SHADER),
    TESS_CONTROL(GL46C.GL_TESS_CONTROL_SHADER),
    TESS_EVALUATION(GL46C.GL_TESS_EVALUATION_SHADER),
    FRAGMENT(GL46C.GL_FRAGMENT_SHADER);

    public final int id;

    ShaderType(int id) {
        this.id = id;
    }

    public static ShaderType fromGlShaderType(int id) {
        for (ShaderType type : values()) {
            if (type.id == id) {
                return type;
            }
        }

        return null;
    }
}
