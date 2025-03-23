package me.jellysquid.mods.sodium.client.render.frapi.helper;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

public abstract class NormalHelper {
    private NormalHelper() { }

    public static void computeFaceNormal(@NotNull Vector3f saveTo, QuadView q) {
        Direction nominalFace = q.nominalFace();

        if (nominalFace != null && GeometryHelper.isQuadParallelToFace(nominalFace, q)) {
            Vec3i vec = nominalFace.getNormal();
            saveTo.set(vec.getX(), vec.getY(), vec.getZ());
            return;
        }

        float dx0 = q.x(2) - q.x(0);
        float dy0 = q.y(2) - q.y(0);
        float dz0 = q.z(2) - q.z(0);
        float dx1 = q.x(3) - q.x(1);
        float dy1 = q.y(3) - q.y(1);
        float dz1 = q.z(3) - q.z(1);

        float normX = dy0 * dz1 - dz0 * dy1;
        float normY = dz0 * dx1 - dx0 * dz1;
        float normZ = dx0 * dy1 - dy0 * dx1;

        float l = (float) Math.sqrt(normX * normX + normY * normY + normZ * normZ);

        if (l != 0) {
            normX /= l;
            normY /= l;
            normZ /= l;
        }

        saveTo.set(normX, normY, normZ);
    }
}