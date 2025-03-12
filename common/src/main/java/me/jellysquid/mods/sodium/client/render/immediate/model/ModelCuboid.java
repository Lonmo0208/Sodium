package me.jellysquid.mods.sodium.client.render.immediate.model;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class ModelCuboid {
    // 保持与Minecraft原生面顺序一致
    public static final int
            FACE_NEG_Y = 0, // DOWN
            FACE_POS_Y = 1, // UP
            FACE_NEG_Z = 2, // NORTH
            FACE_POS_Z = 3, // SOUTH
            FACE_NEG_X = 4, // WEST
            FACE_POS_X = 5; // EAST

    // 坐标范围
    public final float x1, y1, z1;
    public final float x2, y2, z2;

    // 纹理坐标
    public final float u0, u1, u2, u3, u4, u5;
    public final float v0, v1, v2;

    private final int faces;
    public final boolean mirror;

    private static final float INV16 = 1.0f / 16.0f;

    public ModelCuboid(int u, int v,
                       float x, float y, float z,
                       float width, float height, float depth,
                       float inflateX, float inflateY, float inflateZ,
                       boolean mirror,
                       float texWidth, float texHeight,
                       Set<Direction> visibleFaces) {
        // 计算扩展后的边界
        float minX = (x - inflateX) * INV16;
        float minY = (y - inflateY) * INV16;
        float minZ = (z - inflateZ) * INV16;

        float maxX = (x + width + inflateX) * INV16;
        float maxY = (y + height + inflateY) * INV16;
        float maxZ = (z + depth + inflateZ) * INV16;

        // 处理镜像翻转
        if (mirror) {
            float temp = maxX;
            maxX = minX;
            minX = temp;
        }

        this.x1 = minX;
        this.y1 = minY;
        this.z1 = minZ;
        this.x2 = maxX;
        this.y2 = maxY;
        this.z2 = maxZ;

        // 预计算纹理缩放系数
        final float uScale = 1.0f / texWidth;
        final float vScale = 1.0f / texHeight;

        // 计算各面UV坐标
        final float uBase = u * uScale;
        final float uOffsetZ = depth * uScale;
        final float uOffsetX = width * uScale;

        this.u0 = uBase;
        this.u1 = uBase + uOffsetZ;
        this.u2 = this.u1 + uOffsetX;
        this.u3 = this.u2 + uOffsetX;
        this.u4 = this.u3 + uOffsetZ;
        this.u5 = this.u4 + uOffsetX;

        // 计算垂直方向UV
        final float vBase = v * vScale;
        final float vOffsetZ = depth * vScale;
        final float vOffsetY = height * vScale;

        this.v0 = vBase;
        this.v1 = vBase + vOffsetZ;
        this.v2 = this.v1 + vOffsetY;

        this.mirror = mirror;

        // 生成面剔除掩码
        int mask = 0;
        for (Direction face : visibleFaces) {
            mask |= 1 << getFaceIndex(face);
        }
        this.faces = mask;
    }

    public boolean shouldDrawFace(int faceIndex) {
        return (this.faces & (1 << faceIndex)) != 0;
    }

    public static int getFaceIndex(@NotNull Direction dir) {
        return switch (dir) {
            case DOWN -> FACE_NEG_Y;
            case UP -> FACE_POS_Y;
            case NORTH -> FACE_NEG_Z;
            case SOUTH -> FACE_POS_Z;
            case WEST -> FACE_NEG_X;
            case EAST -> FACE_POS_X;
        };
    }
}