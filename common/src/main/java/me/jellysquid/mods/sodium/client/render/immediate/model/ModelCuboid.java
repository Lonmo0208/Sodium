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

        final float uScale = 1.0f / texWidth;
        final float vScale = 1.0f / texHeight;

        final float uBase = u * uScale;
        final float uDepth = depth * uScale;
        final float uWidth = width * uScale;

        this.u0 = uBase;
        this.u1 = uBase + uDepth;
        this.u2 = this.u1 + uWidth;
        this.u3 = this.u2 + uWidth;
        this.u4 = this.u3 + uDepth;
        this.u5 = this.u4 + uWidth;

        final float vBase = v * vScale;
        final float vDepth = depth * vScale;

        this.v0 = vBase;
        this.v1 = vBase + vDepth;
        this.v2 = this.v1 + (height * vScale);

        this.mirror = mirror;

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