package me.jellysquid.mods.sodium.mixin.core.render.immediate.consumer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import me.jellysquid.mods.sodium.client.render.vertex.buffer.ExtendedBufferBuilder;
import me.jellysquid.mods.sodium.client.render.vertex.buffer.SodiumBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiBufferSource.BufferSource.class)
public class BufferSourceMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"), cancellable = true)
    private void useFasterVertexConsumer(RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
        VertexConsumer result = cir.getReturnValue();
        if (result instanceof ExtendedBufferBuilder bufferBuilder) {
            SodiumBufferBuilder replacement = bufferBuilder.sodium$getDelegate();
            if (replacement != null) cir.setReturnValue(replacement);
        }
    }
@ModifyVariable(
        method = {"method_24213", "lambda$endBatch$0", "m_109916_"},
        require = 1,
        at = @At(value = "LOAD", ordinal = 0)
)
private VertexConsumer changeComparedVertexConsumer(VertexConsumer vertexConsumer) {
    return (vertexConsumer instanceof SodiumBufferBuilder bufferBuilder)
            ? bufferBuilder.getOriginalBufferBuilder()
            : vertexConsumer;
}
}