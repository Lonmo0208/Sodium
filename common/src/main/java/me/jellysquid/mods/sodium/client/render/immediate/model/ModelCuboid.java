package me.jellysquid.mods.sodium.client.render.immediate.model;

import net.minecraft.core.Direction;
import java.util.Set;

public class ModelCuboid {
    public final float x1, y1, z1;
    public final float x2, y2, z2;

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
        // 坐标计算（保持不变）
        float minX = (x - inflateX) * INV16;
        float maxX = (x + width + inflateX) * INV16;

        if (mirror) {
            float temp = maxX;
            maxX = minX;
            minX = temp;
        }

        this.x1 = minX;
        this.y1 = (y - inflateY) * INV16;
        this.z1 = (z - inflateZ) * INV16;
        this.x2 = maxX;
        this.y2 = (y + height + inflateY) * INV16;
        this.z2 = (z + depth + inflateZ) * INV16;

        // 修复UV计算（恢复原始逻辑）
        float scaleU = 1.0f / texWidth;
        float scaleV = 1.0f / texHeight;

        this.u0 = scaleU * u;
        this.u1 = scaleU * (u + depth);
        this.u2 = scaleU * (u + depth + width);
        this.u3 = scaleU * (u + depth + width + width);
        this.u4 = scaleU * (u + depth + width + depth);
        this.u5 = scaleU * (u + depth + width + depth + width);

        this.v0 = scaleV * v;
        this.v1 = scaleV * (v + depth);
        this.v2 = scaleV * (v + depth + height);

        this.mirror = mirror;

        // 面可见性计算（保持不变）
        int mask = 0;
        for (Direction face : visibleFaces) {
            mask |= 1 << face.ordinal();
        }
        this.faces = mask;
    }

    public boolean shouldDrawFace(int faceIndex) {
        return (this.faces & (1 << faceIndex)) != 0;
    }
}