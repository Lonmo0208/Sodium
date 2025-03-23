package me.jellysquid.mods.sodium.mixin.core.render.immediate.consumer;

import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.api.vertex.attributes.CommonVertexAttribute;
import net.caffeinemc.mods.sodium.api.vertex.attributes.common.ColorAttribute;
import net.caffeinemc.mods.sodium.api.vertex.attributes.common.TextureAttribute;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SheetedDecalTextureGenerator.class)
public class SheetedDecalTextureGeneratorMixin implements VertexBufferWriter {
    @Shadow @Final private VertexConsumer delegate;
    @Shadow @Final private Matrix3f normalInversePose;
    @Shadow @Final private Matrix4f cameraInversePose;
    @Shadow @Final private float textureScale;
    @Unique private boolean canUseIntrinsics;
    @Unique private final Vector3f normal = new Vector3f();
    @Unique private final Vector4f position = new Vector4f();
    @Inject(method = "<init>", at = @At("RETURN"))
private void onInit(CallbackInfo ci) {
    this.canUseIntrinsics = VertexBufferWriter.tryOf(this.delegate) != null;
}

@Override public boolean canUseIntrinsics() { return this.canUseIntrinsics; }

@Override
public void push(MemoryStack stack, long ptr, int count, VertexFormatDescription format) {
    transform(ptr, count, format, normalInversePose, cameraInversePose, textureScale);
    VertexBufferWriter.of(this.delegate).push(stack, ptr, count, format);
}

@Unique
private void transform(long ptr, int count, VertexFormatDescription format,
                       Matrix3f inverseNormalMatrix, Matrix4f inverseTextureMatrix, float textureScale) {
    final long stride = format.stride();
    final long posOffset = format.getElementOffset(CommonVertexAttribute.POSITION);
    final long colorOffset = format.getElementOffset(CommonVertexAttribute.COLOR);
    final long normalOffset = format.getElementOffset(CommonVertexAttribute.NORMAL);
    final long texOffset = format.getElementOffset(CommonVertexAttribute.TEXTURE);
    final int color = ColorABGR.pack(1.0f, 1.0f, 1.0f, 1.0f);
    final long end = ptr + count * stride;

    while (ptr < end) {
        position.set(
                MemoryUtil.memGetFloat(ptr + posOffset),
                MemoryUtil.memGetFloat(ptr + posOffset + 4),
                MemoryUtil.memGetFloat(ptr + posOffset + 8),
                1.0f
        );

        int packedNormal = MemoryUtil.memGetInt(ptr + normalOffset);
        normal.set(NormI8.unpackX(packedNormal), NormI8.unpackY(packedNormal), NormI8.unpackZ(packedNormal));

        Vector3f transformedNormal = inverseNormalMatrix.transform(normal, new Vector3f());
        Direction direction = Direction.getNearest(transformedNormal.x(), transformedNormal.y(), transformedNormal.z());

        Vector4f transformedTexture = inverseTextureMatrix.transform(position, new Vector4f());
        transformedTexture.rotateY(3.1415927F)
                .rotateX(-1.5707964F)
                .rotate(direction.getRotation());

        float u = -transformedTexture.x() * textureScale;
        float v = -transformedTexture.y() * textureScale;

        ColorAttribute.set(ptr + colorOffset, color);
        TextureAttribute.put(ptr + texOffset, u, v);

        ptr += stride;
    }
}
}