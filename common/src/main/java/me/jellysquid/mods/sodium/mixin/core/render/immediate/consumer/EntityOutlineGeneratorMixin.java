package me.jellysquid.mods.sodium.mixin.core.render.immediate.consumer;

import com.mojang.blaze3d.vertex.DefaultedVertexConsumer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.vertex.attributes.CommonVertexAttribute;
import net.caffeinemc.mods.sodium.api.vertex.attributes.common.ColorAttribute;
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

@Mixin(targets = "net/minecraft/client/renderer/OutlineBufferSource$EntityOutlineGenerator")
public abstract class EntityOutlineGeneratorMixin extends DefaultedVertexConsumer implements VertexBufferWriter {
    @Shadow @Final private VertexConsumer delegate;
    @Unique private boolean canUseIntrinsics;
    @Inject(method = "<init>", at = @At("RETURN"))
private void onInit(CallbackInfo ci) {
    this.canUseIntrinsics = VertexBufferWriter.tryOf(this.delegate) != null;
}

@Override public boolean canUseIntrinsics() { return this.canUseIntrinsics; }

@Override
public void push(MemoryStack stack, long ptr, int count, VertexFormatDescription format) {
    final int color = ColorABGR.pack(this.defaultR, this.defaultG, this.defaultB, this.defaultA);
    transform(ptr, count, format, color);
    VertexBufferWriter.of(this.delegate).push(stack, ptr, count, format);
}

@Unique
private static void transform(long ptr, int count, VertexFormatDescription format, int color) {
    final long stride = format.stride();
    final long offset = format.getElementOffset(CommonVertexAttribute.COLOR);
    final long end = ptr + count * stride;

    while (ptr < end) {
        ColorAttribute.set(ptr + offset, color);
        ptr += stride;
    }
}
}