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
    private static final int[] DIRECTION_MASKS = new int[6];

    static {
        for (Direction dir : Direction.values()) {
            DIRECTION_MASKS[dir.ordinal()] = 1 << dir.ordinal();
        }
    }

    public ModelCuboid(int u, int v, float x, float y, float z,
                       float width, float height, float depth,
                       float inflateX, float inflateY, float inflateZ,
                       boolean mirror, float texWidth, float texHeight,
                       Set<Direction> visibleFaces) {
        float baseX = x * INV16;
        float scaledWidth = width * INV16;
        this.x1 = mirror ?
                (baseX + scaledWidth + inflateX * INV16) :
                (baseX - inflateX * INV16);
        this.x2 = mirror ?
                (baseX - inflateX * INV16) :
                (baseX + scaledWidth + inflateX * INV16);

        this.y1 = (y - inflateY) * INV16;
        this.z1 = (z - inflateZ) * INV16;
        this.y2 = (y + height + inflateY) * INV16;
        this.z2 = (z + depth + inflateZ) * INV16;

        final float scaleU = 1.0f / texWidth;
        final float scaleV = 1.0f / texHeight;

        final int uDepth = u + (int)depth;
        final int uDepthWidth = uDepth + (int)width;
        this.u0 = scaleU * u;
        this.u1 = scaleU * uDepth;
        this.u2 = scaleU * uDepthWidth;
        this.u3 = scaleU * (uDepthWidth + (int)width);
        this.u4 = scaleU * (uDepthWidth + (int)depth);
        this.u5 = scaleU * (uDepthWidth + (int)depth + (int)width);

        final int vDepth = v + (int)depth;
        this.v0 = scaleV * v;
        this.v1 = scaleV * vDepth;
        this.v2 = scaleV * (vDepth + (int)height);

        this.mirror = mirror;

        int faceMask = 0;
        for (Direction face : visibleFaces) {
            faceMask |= DIRECTION_MASKS[face.ordinal()];
        }
        this.faces = faceMask;
    }

    public boolean shouldDrawFace(int faceIndex) {
        return (this.faces & DIRECTION_MASKS[faceIndex]) != 0;
    }
}