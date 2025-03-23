package me.jellysquid.mods.sodium.mixin.core.render.immediate.consumer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class VertexMultiConsumerMixin {
    @Mixin(targets = "com/mojang/blaze3d/vertex/VertexMultiConsumer$Double")
    public static class DoubleMixin implements VertexBufferWriter {
        @Shadow @Final private VertexConsumer first;
        @Shadow @Final private VertexConsumer second;
        @Unique private boolean canUseIntrinsics;

        @Inject(method = "<init>", at = @At("RETURN"))
        private void checkFullStatus(CallbackInfo ci) {
            this.canUseIntrinsics = VertexBufferWriter.tryOf(this.first) != null
                    && VertexBufferWriter.tryOf(this.second) != null;
        }

        @Override public boolean canUseIntrinsics() { return this.canUseIntrinsics; }

        @Override
        public void push(MemoryStack stack, long ptr, int count, VertexFormatDescription format) {
            VertexBufferWriter firstWriter = VertexBufferWriter.of(this.first);
            VertexBufferWriter secondWriter = VertexBufferWriter.of(this.second);
            VertexBufferWriter.copyInto(firstWriter, stack, ptr, count, format);
            VertexBufferWriter.copyInto(secondWriter, stack, ptr, count, format);
        }
    }

    @Mixin(targets = "com/mojang/blaze3d/vertex/VertexMultiConsumer$Multiple")
    public static class MultipleMixin implements VertexBufferWriter {
        @Shadow @Final private VertexConsumer[] delegates;
        @Unique private boolean canUseIntrinsics;

        @Inject(method = "<init>", at = @At("RETURN"))
        private void checkFullStatus(CallbackInfo ci) {
            this.canUseIntrinsics = allDelegatesSupportIntrinsics();
        }

        @Unique
        private boolean allDelegatesSupportIntrinsics() {
            for (VertexConsumer delegate : this.delegates) {
                if (VertexBufferWriter.tryOf(delegate) == null) return false;
            }
            return true;
        }

        @Override public boolean canUseIntrinsics() { return this.canUseIntrinsics; }

        @Override
        public void push(MemoryStack stack, long ptr, int count, VertexFormatDescription format) {
            for (VertexConsumer delegate : this.delegates) {
                VertexBufferWriter.copyInto(VertexBufferWriter.of(delegate), stack, ptr, count, format);
            }
        }
    }
}