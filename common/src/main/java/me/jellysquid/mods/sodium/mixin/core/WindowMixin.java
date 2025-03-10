package me.jellysquid.mods.sodium.mixin.core;

import com.mojang.blaze3d.platform.Window;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.compatibility.workarounds.Workarounds;
import me.jellysquid.mods.sodium.client.platform.NativeWindowHandle;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.spongepowered.asm.mixin.*;

@Mixin(Window.class)
public class WindowMixin implements NativeWindowHandle {
    @Shadow @Final private long handle;
    //@Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"), require = 0)
    public long setAdditionalWindowHints(int width, int height, CharSequence title, long monitor, long share) {
        if (SodiumClientMod.options().performance.useNoErrorGLContext &&
                !Workarounds.isWorkaroundEnabled(Workarounds.Reference.NO_ERROR_CONTEXT_UNSUPPORTED)) {
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_NO_ERROR, GLFW.GLFW_TRUE);
        }
        return GLFW.glfwCreateWindow(width, height, title, monitor, share);
    }

    @Override
    public long getWin32Handle() {
        return GLFWNativeWin32.glfwGetWin32Window(this.handle);
    }
}

