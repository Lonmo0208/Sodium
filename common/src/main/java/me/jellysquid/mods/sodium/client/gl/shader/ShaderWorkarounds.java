package me.jellysquid.mods.sodium.client.gl.shader;

import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

class ShaderWorkarounds {
    static void safeShaderSource(int glId, CharSequence source) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, true);
            PointerBuffer pointers = stack.mallocPointer(1);
            pointers.put(sourceBuffer);

            GL20C.nglShaderSource(glId, 1, pointers.address0(), 0);
        }
    }
}