package net.caffeinemc.mods.sodium.client.gl.shader;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.caffeinemc.mods.sodium.client.gl.GlObject;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL46C;

import java.util.Arrays;

/**
 * A compiled OpenGL shader object.
 */
public class GlShader extends GlObject {
    private static final Logger LOGGER = LogManager.getLogger(GlShader.class);

    private final Identifier name;

    public GlShader(ShaderType type, Identifier name, ShaderParser.ParsedShader parsedShader) {
        this.name = name;

        int handle = GL46C.glCreateShader(type.id);
        ShaderWorkarounds.safeShaderSource(handle, parsedShader.src());
        GL46C.glCompileShader(handle);

        String log = GL46C.glGetShaderInfoLog(handle);

        if (!log.isEmpty()) {
            LOGGER.warn("Shader compilation log for {}: {}", this.name, log);
            LOGGER.warn("Include table: {}", Arrays.toString(parsedShader.includeIds()));
        }

        int result = GlStateManager.glGetShaderi(handle, GL46C.GL_COMPILE_STATUS);

        if (result != GL46C.GL_TRUE) {
            throw new RuntimeException("Shader compilation failed, see log for details");
        }

        this.setHandle(handle);
    }

    public Identifier getName() {
        return this.name;
    }

    public void delete() {
        GL46C.glDeleteShader(this.handle());

        this.invalidateHandle();
    }
}
