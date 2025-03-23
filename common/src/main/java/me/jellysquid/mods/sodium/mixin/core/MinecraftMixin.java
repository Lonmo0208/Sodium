package me.jellysquid.mods.sodium.mixin.core;

import com.mojang.realmsclient.client.RealmsClient;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.checks.ResourcePackScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.lwjgl.opengl.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.*;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    @Final
    private ReloadableResourceManager resourceManager;

    @Unique
    private final LongArrayFIFOQueue sodium$fences = new LongArrayFIFOQueue();

    @Unique
    private static final int SYNC_FLUSH_COMMANDS_BIT = GL32C.GL_SYNC_FLUSH_COMMANDS_BIT;
    @Unique
    private static final long SYNC_GPU_COMMANDS_COMPLETE = GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE;

    @Inject(method = "runTick", at = @At("HEAD"))
    private void preRender(boolean tick, CallbackInfo ci) {
        final ProfilerFiller profiler = Minecraft.getInstance().getProfiler();
        profiler.push("wait_for_gpu");

        final int maxFences = SodiumClientMod.options().advanced.cpuRenderAheadLimit;
        while (this.sodium$fences.size() > maxFences) {
            final long fence = this.sodium$fences.dequeueLong();
            // 使用非阻塞方式检查fence状态
            int result = GL32C.glClientWaitSync(fence, SYNC_FLUSH_COMMANDS_BIT, 0);
            if (result == GL32C.GL_ALREADY_SIGNALED || result == GL32C.GL_CONDITION_SATISFIED) {
                GL32C.glDeleteSync(fence);
            } else {
                // 如果未完成，将fence放回队列
                this.sodium$fences.enqueue(fence);
                break;
            }
        }

        profiler.pop();
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void postRender(boolean tick, CallbackInfo ci) {
        final long fence = GL32C.glFenceSync((int) SYNC_GPU_COMMANDS_COMPLETE, 0);
        if (fence == 0) {
            throw new RuntimeException("Failed to create fence object");
        }
        this.sodium$fences.enqueue(fence);
    }

    @Inject(method = "setInitialScreen", at = @At("TAIL"))
    private void postInit(RealmsClient realmsClient, ReloadInstance reloadInstance, GameConfig.QuickPlayData quickPlayData, CallbackInfo ci) {
        sodium$checkCoreShaders();
    }

    @Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("TAIL"))
    private void postResourceReload(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        sodium$checkCoreShaders();
    }

    @Unique
    private void sodium$checkCoreShaders() {
        ResourcePackScanner.checkIfCoreShaderLoaded(this.resourceManager);
    }
}
