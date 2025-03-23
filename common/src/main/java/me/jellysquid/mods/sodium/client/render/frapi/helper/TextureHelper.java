package me.jellysquid.mods.sodium.client.render.frapi.helper;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

public class TextureHelper {
    private TextureHelper() { }

    private static final float NORMALIZER = 1f / 16f;

    public static void bakeSprite(MutableQuadView quad, TextureAtlasSprite sprite, int bakeFlags) {
        if (quad.nominalFace() != null && (MutableQuadView.BAKE_LOCK_UV & bakeFlags) != 0) {
            applyModifier(quad, UVLOCKERS[quad.nominalFace().get3DDataValue()]);
        } else if ((MutableQuadView.BAKE_NORMALIZED & bakeFlags) == 0) {
            applyModifier(quad, (q, i) -> q.uv(i, q.u(i) * NORMALIZER, q.v(i) * NORMALIZER));
        }

        int rotation = bakeFlags & 3;

        if (rotation != 0) {
            applyModifier(quad, ROTATIONS[rotation]);
        }

        if ((MutableQuadView.BAKE_FLIP_U & bakeFlags) != 0) {
            applyModifier(quad, (q, i) -> q.uv(i, 1 - q.u(i), q.v(i)));
        }

        if ((MutableQuadView.BAKE_FLIP_V & bakeFlags) != 0) {
            applyModifier(quad, (q, i) -> q.uv(i, q.u(i), 1 - q.v(i)));
        }

        interpolate(quad, sprite);
    }

    private static void interpolate(MutableQuadView q, TextureAtlasSprite sprite) {
        float uMin = sprite.getU0();
        float uSpan = sprite.getU1() - uMin;
        float vMin = sprite.getV0();
        float vSpan = sprite.getV1() - vMin;

        for (int i = 0; i < 4; i++) {
            q.uv(i, uMin + q.u(i) * uSpan, vMin + q.v(i) * vSpan);
        }
    }

    @FunctionalInterface
    private interface VertexModifier {
        void apply(MutableQuadView quad, int vertexIndex);
    }

    private static void applyModifier(MutableQuadView quad, VertexModifier modifier) {
        for (int i = 0; i < 4; i++) {
            modifier.apply(quad, i);
        }
    }

    private static final VertexModifier[] ROTATIONS = {
            null,
            (q, i) -> q.uv(i, q.v(i), 1 - q.u(i)),
            (q, i) -> q.uv(i, 1 - q.u(i), 1 - q.v(i)),
            (q, i) -> q.uv(i, 1 - q.v(i), q.u(i))
    };

    private static final VertexModifier[] UVLOCKERS = new VertexModifier[6];

    static {
        UVLOCKERS[Direction.EAST.get3DDataValue()] = (q, i) -> q.uv(i, 1 - q.z(i), 1 - q.y(i));
        UVLOCKERS[Direction.WEST.get3DDataValue()] = (q, i) -> q.uv(i, q.z(i), 1 - q.y(i));
        UVLOCKERS[Direction.NORTH.get3DDataValue()] = (q, i) -> q.uv(i, 1 - q.x(i), 1 - q.y(i));
        UVLOCKERS[Direction.SOUTH.get3DDataValue()] = (q, i) -> q.uv(i, q.x(i), 1 - q.y(i));
        UVLOCKERS[Direction.DOWN.get3DDataValue()] = (q, i) -> q.uv(i, q.x(i), 1 - q.z(i));
        UVLOCKERS[Direction.UP.get3DDataValue()] = (q, i) -> q.uv(i, q.x(i), q.z(i));
    }
}