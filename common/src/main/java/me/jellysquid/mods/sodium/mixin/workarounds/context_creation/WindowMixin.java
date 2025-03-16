package me.jellysquid.mods.sodium.mixin.workarounds.context_creation;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import me.jellysquid.mods.sodium.client.compatibility.checks.ModuleScanner;
import me.jellysquid.mods.sodium.client.compatibility.checks.PostLaunchChecks;
import me.jellysquid.mods.sodium.client.compatibility.environment.GLContextInfo;
import me.jellysquid.mods.sodium.client.compatibility.workarounds.Workarounds;
import me.jellysquid.mods.sodium.client.compatibility.workarounds.nvidia.NvidiaWorkarounds;
import me.jellysquid.mods.sodium.client.platform.NativeWindowHandle;
import me.jellysquid.mods.sodium.client.services.PlatformInfoAccess;
import net.minecraft.Util;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.WGL;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;


@Mixin(Window.class)
public class WindowMixin {
    @Shadow
    @Final
    private static Logger LOGGER;

    @Unique
    private long wglPrevContext = MemoryUtil.NULL;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"), expect = 0, require = 0)
    private long wrapGlfwCreateWindow(int width, int height, CharSequence title, long monitor, long share) {
        NvidiaWorkarounds.applyEnvironmentChanges();

        try {
            return GLFW.glfwCreateWindow(width, height, title, monitor, share);
        } finally {
            NvidiaWorkarounds.undoEnvironmentChanges();
        }
    }

    @SuppressWarnings("all")
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/neoforged/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J"), expect = 0, require = 0)
    private long wrapGlfwCreateWindowForge(final IntSupplier width, final IntSupplier height, final Supplier<String> title, final LongSupplier monitor, Operation<Long> op) {
        final boolean applyNvidiaWorkarounds = Workarounds.isWorkaroundEnabled(Workarounds.Reference.NVIDIA_THREADED_OPTIMIZATIONS_BROKEN);

        if (applyNvidiaWorkarounds && !PlatformInfoAccess.getInstance().platformHasEarlyLoadingScreen()) {
            NvidiaWorkarounds.applyEnvironmentChanges();
        }

        try {
            return op.call(width, height, title, monitor);
        } finally {
            if (applyNvidiaWorkarounds && !PlatformInfoAccess.getInstance().platformHasEarlyLoadingScreen()) {
                NvidiaWorkarounds.undoEnvironmentChanges();
            }
        }
    }


    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL;createCapabilities()Lorg/lwjgl/opengl/GLCapabilities;"
            )
    )
    private GLCapabilities postContextReady() {
        GLCapabilities capabilities = GL.createCapabilities();
        GLContextInfo context = GLContextInfo.create();
        if (context == null) {
            LOGGER.warn("Could not retrieve identifying strings for OpenGL implementation");
        } else {
            LOGGER.info("OpenGL Vendor: {}", context.vendor());
            LOGGER.info("OpenGL Renderer: {}", context.renderer());
            LOGGER.info("OpenGL Version: {}", context.version());
        }

        this.wglPrevContext = (Util.getPlatform() == Util.OS.WINDOWS)
                ? WGL.wglGetCurrentContext()
                : MemoryUtil.NULL;


        PostLaunchChecks.onContextInitialized((NativeWindowHandle) this, context);
        ModuleScanner.checkModules((NativeWindowHandle) this);

        return capabilities;
    }

    @Inject(method = "updateDisplay", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;flipFrame(J)V", shift = At.Shift.AFTER))
    private void preSwapBuffers(CallbackInfo ci) {
        if (this.wglPrevContext == MemoryUtil.NULL) {
            // There is no prior recorded context.
            return;
        }

        var context = WGL.wglGetCurrentContext();

        if (this.wglPrevContext == context) {
            // The context has not changed.
            return;
        }

        // Something has decided to replace the OpenGL context, which is not a good sign
        LOGGER.warn("The OpenGL context appears to have been suddenly replaced! Something has likely just injected into the game process.");

        // Likely, this indicates a module was injected into the current process. We should check that
        // nothing problematic was just installed.
        ModuleScanner.checkModules((NativeWindowHandle) this);

        // If we didn't find anything problematic (which would have thrown an exception), then let's just record
        // the new context pointer and carry on.
        this.wglPrevContext = context;
    }
}
