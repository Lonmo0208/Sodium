package me.jellysquid.mods.sodium.client.gl.shader.uniform;

import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

public class GlUniformMatrix4f extends GlUniform<Matrix4fc>  {
    private final FloatBuffer buffer;

    public GlUniformMatrix4f(int index) {
        super(index);
        this.buffer = MemoryStack.stackGet().mallocFloat(16);
    }

    @Override
    public void set(Matrix4fc value) {
        buffer.clear();
        value.get(buffer);
        GL30C.glUniformMatrix4fv(this.index, false, buffer);
    }
}