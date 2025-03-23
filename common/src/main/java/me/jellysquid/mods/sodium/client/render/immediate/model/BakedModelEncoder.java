package me.jellysquid.mods.sodium.client.render.immediate.model;

import com.mojang.blaze3d.vertex.PoseStack;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.render.frapi.helper.ColorHelper;
import net.caffeinemc.mods.sodium.api.math.MatrixHelper;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.ColorU8;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ModelVertex;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public class BakedModelEncoder {
private static int mergeLighting(int stored, int calculated) {
    if (stored == 0) return calculated;
    int blockLight = Math.max(stored & 0xFFFF, calculated & 0xFFFF);
    int skyLight = Math.max((stored >> 16) & 0xFFFF, (calculated >> 16) & 0xFFFF);
    return blockLight | (skyLight << 16);
}

public static void writeQuadVertices(VertexBufferWriter writer, PoseStack.Pose matrices, ModelQuadView quad, int color, int light, int overlay) {
    Matrix3f matNormal = matrices.normal();
    Matrix4f matPosition = matrices.pose();

    try (MemoryStack stack = MemoryStack.stackPush()) {
        long buffer = stack.nmalloc(4 * ModelVertex.STRIDE);
        long ptr = buffer;

        for (int i = 0; i < 4; i++) {
            float x = quad.getX(i);
            float y = quad.getY(i);
            float z = quad.getZ(i);

            int newLight = mergeLighting(quad.getLight(i), light);
            int normal = MatrixHelper.transformNormal(matNormal, true, quad.getAccurateNormal(i));

            float xt = MatrixHelper.transformPositionX(matPosition, x, y, z);
            float yt = MatrixHelper.transformPositionY(matPosition, x, y, z);
            float zt = MatrixHelper.transformPositionZ(matPosition, x, y, z);

            ModelVertex.write(ptr, xt, yt, zt, ColorHelper.multiplyColor(color, quad.getColor(i)),
                    quad.getTexU(i), quad.getTexV(i), overlay, newLight, normal);
            ptr += ModelVertex.STRIDE;
        }

        writer.push(stack, buffer, 4, ModelVertex.FORMAT);
    }
}

public static void writeQuadVertices(VertexBufferWriter writer, PoseStack.Pose matrices, ModelQuadView quad,
                                     float r, float g, float b, float a, float[] brightnessTable,
                                     boolean colorize, int[] light, int overlay) {
    Matrix3f matNormal = matrices.normal();
    Matrix4f matPosition = matrices.pose();

    try (MemoryStack stack = MemoryStack.stackPush()) {
        long buffer = stack.nmalloc(4 * ModelVertex.STRIDE);
        long ptr = buffer;

        for (int i = 0; i < 4; i++) {
            float x = quad.getX(i);
            float y = quad.getY(i);
            float z = quad.getZ(i);

            float xt = MatrixHelper.transformPositionX(matPosition, x, y, z);
            float yt = MatrixHelper.transformPositionY(matPosition, x, y, z);
            float zt = MatrixHelper.transformPositionZ(matPosition, x, y, z);

            int normal = MatrixHelper.transformNormal(matNormal, true, quad.getAccurateNormal(i));
            float brightness = brightnessTable[i];

            float fR, fG, fB;
            if (colorize) {
                int color = quad.getColor(i);
                fR = ColorU8.byteToNormalizedFloat(ColorABGR.unpackRed(color)) * brightness * r;
                fG = ColorU8.byteToNormalizedFloat(ColorABGR.unpackGreen(color)) * brightness * g;
                fB = ColorU8.byteToNormalizedFloat(ColorABGR.unpackBlue(color)) * brightness * b;
            } else {
                fR = brightness * r;
                fG = brightness * g;
                fB = brightness * b;
            }

            int color = ColorABGR.pack(fR, fG, fB, a);
            ModelVertex.write(ptr, xt, yt, zt, color, quad.getTexU(i), quad.getTexV(i), overlay, light[i], normal);
            ptr += ModelVertex.STRIDE;
        }

        writer.push(stack, buffer, 4, ModelVertex.FORMAT);
    }
}
}