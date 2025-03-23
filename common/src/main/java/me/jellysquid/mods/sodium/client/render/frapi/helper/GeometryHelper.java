package me.jellysquid.mods.sodium.client.render.frapi.helper;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

public abstract class GeometryHelper {
    private GeometryHelper() { }

    public static boolean isQuadParallelToFace(Direction face, QuadView quad) {
        int i = face.getAxis().ordinal();
        float val = quad.posByIndex(0, i);
        return Math.abs(val - quad.posByIndex(1, i)) < 1.0E-5F &&
                Math.abs(val - quad.posByIndex(2, i)) < 1.0E-5F &&
                Math.abs(val - quad.posByIndex(3, i)) < 1.0E-5F;
    }

    public static Direction lightFace(QuadView quad) {
        Vector3f normal = quad.faceNormal();
        switch (longestAxis(normal)) {
            case X: return normal.x() > 0 ? Direction.EAST : Direction.WEST;
            case Y: return normal.y() > 0 ? Direction.UP : Direction.DOWN;
            case Z: return normal.z() > 0 ? Direction.SOUTH : Direction.NORTH;
            default: return Direction.UP;
        }
    }

    public static Direction.Axis longestAxis(Vector3f vec) {
        return longestAxis(vec.x(), vec.y(), vec.z());
    }

    public static Direction.Axis longestAxis(float normalX, float normalY, float normalZ) {
        Direction.Axis result = Direction.Axis.Y;
        float longest = Math.abs(normalY);
        float a = Math.abs(normalX);

        if (a > longest) {
            result = Direction.Axis.X;
            longest = a;
        }

        return Math.abs(normalZ) > longest ? Direction.Axis.Z : result;
    }
}