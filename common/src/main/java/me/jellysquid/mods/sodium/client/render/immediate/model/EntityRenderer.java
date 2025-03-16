package me.jellysquid.mods.sodium.client.render.immediate.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.caffeinemc.mods.sodium.api.math.MatrixHelper;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ModelVertex;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.apache.commons.lang3.ArrayUtils;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

public class EntityRenderer {
    private static final int NUM_CUBE_VERTICES = 8;
    private static final int NUM_CUBE_FACES = 6;
    private static final int NUM_FACE_VERTICES = 4;

    private static final int
            FACE_NEG_Y = 0,
            FACE_POS_Y = 1,
            FACE_NEG_Z = 2,
            FACE_POS_Z = 3,
            FACE_NEG_X = 4,
            FACE_POS_X = 5;

    private static final int
            VERTEX_X1_Y1_Z1 = 0,
            VERTEX_X2_Y1_Z1 = 1,
            VERTEX_X2_Y2_Z1 = 2,
            VERTEX_X1_Y2_Z1 = 3,
            VERTEX_X1_Y1_Z2 = 4,
            VERTEX_X2_Y1_Z2 = 5,
            VERTEX_X2_Y2_Z2 = 6,
            VERTEX_X1_Y2_Z2 = 7;

    private static final Vector3f[] CUBE_CORNERS = new Vector3f[NUM_CUBE_VERTICES];
    private static final Vector3f[][][] POSITION_CACHE = new Vector3f[2][NUM_CUBE_FACES][NUM_FACE_VERTICES];
    private static final Vector2f[][] VERTEX_TEXTURES = new Vector2f[NUM_CUBE_FACES][NUM_FACE_VERTICES];
    private static final Vector2f[][] VERTEX_TEXTURES_MIRRORED = new Vector2f[NUM_CUBE_FACES][NUM_FACE_VERTICES];

    private static final Matrix3f LAST_NORMAL_MATRIX = new Matrix3f();
    private static final int[] CUBE_NORMALS = new int[NUM_CUBE_FACES];
    private static final int[] CUBE_NORMALS_MIRRORED = new int[NUM_CUBE_FACES];

    static {
        for (int i = 0; i < NUM_CUBE_VERTICES; i++) {
            CUBE_CORNERS[i] = new Vector3f();
        }

        final int[][] CUBE_VERTICES = {
                {5, 4, 0, 1}, // FACE_NEG_Y
                {2, 3, 7, 6}, // FACE_POS_Y
                {1, 0, 3, 2}, // FACE_NEG_Z
                {4, 5, 6, 7}, // FACE_POS_Z
                {5, 1, 2, 6}, // FACE_NEG_X
                {0, 4, 7, 3}  // FACE_POS_X
        };

        for (int face = 0; face < NUM_CUBE_FACES; face++) {
            for (int vert = 0; vert < NUM_FACE_VERTICES; vert++) {
                POSITION_CACHE[0][face][vert] = CUBE_CORNERS[CUBE_VERTICES[face][vert]];
                POSITION_CACHE[1][face][vert] = CUBE_CORNERS[CUBE_VERTICES[face][3 - vert]];

                VERTEX_TEXTURES[face][vert] = new Vector2f();
                VERTEX_TEXTURES_MIRRORED[face][vert] = new Vector2f();
            }
        }
    }

    public static void render(PoseStack poseStack, VertexBufferWriter writer, ModelPart part, int light, int overlay, int color) {
        ModelPartData accessor = ModelPartData.from(part);
        if (!accessor.isVisible()) return;

        ModelCuboid[] cuboids = accessor.getCuboids();
        ModelPart[] children = accessor.getChildren();
        if (ArrayUtils.isEmpty(cuboids) && ArrayUtils.isEmpty(children)) return;

        poseStack.pushPose();
        part.translateAndRotate(poseStack);

        if (!accessor.isHidden()) {
            renderCuboids(poseStack.last(), writer, cuboids, light, overlay, color);
        }

        renderChildren(poseStack, writer, children, light, overlay, color);
        poseStack.popPose();
    }

    private static void renderCuboids(PoseStack.Pose matrices, VertexBufferWriter writer, ModelCuboid[] cuboids, int light, int overlay, int color) {
        updateNormalCache(matrices);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long buffer = stack.nmalloc(ModelVertex.STRIDE, NUM_CUBE_FACES * NUM_FACE_VERTICES * ModelVertex.STRIDE);

            for (ModelCuboid cuboid : cuboids) {
                prepareVertices(matrices, cuboid);
                int vertexCount = emitQuads(buffer, cuboid, color, overlay, light);

                if (vertexCount > 0) {
                    writer.push(stack, buffer, vertexCount, ModelVertex.FORMAT);
                }
            }
        }
    }

    private static int emitQuads(long buffer, ModelCuboid cuboid, int color, int overlay, int light) {
        final int mirrorFlag = cuboid.mirror ? 1 : 0;
        int vertexCount = 0;
        long ptr = buffer;

        for (int face = 0; face < NUM_CUBE_FACES; face++) {
            if (!cuboid.shouldDrawFace(face)) continue;

            final Vector3f[] positions = POSITION_CACHE[mirrorFlag][face];
            final Vector2f[] textures = cuboid.mirror ? VERTEX_TEXTURES_MIRRORED[face] : VERTEX_TEXTURES[face];
            final int normal = cuboid.mirror ? CUBE_NORMALS_MIRRORED[face] : CUBE_NORMALS[face];

            for (int vert = 0; vert < NUM_FACE_VERTICES; vert++) {
                ModelVertex.write(ptr,
                        positions[vert].x, positions[vert].y, positions[vert].z,
                        color, textures[vert].x, textures[vert].y,
                        overlay, light, normal
                );
                ptr += ModelVertex.STRIDE;
            }
            vertexCount += 4;
        }
        return vertexCount;
    }

    private static void updateNormalCache(PoseStack.Pose matrices) {
        Matrix3f normalMatrix = matrices.normal();
        if (normalMatrix.equals(LAST_NORMAL_MATRIX)) return;

        LAST_NORMAL_MATRIX.set(normalMatrix);

        CUBE_NORMALS[FACE_NEG_Y] = MatrixHelper.transformNormal(normalMatrix, true, Direction.DOWN);
        CUBE_NORMALS[FACE_POS_Y] = MatrixHelper.transformNormal(normalMatrix, true, Direction.UP);
        CUBE_NORMALS[FACE_NEG_Z] = MatrixHelper.transformNormal(normalMatrix, true, Direction.NORTH);
        CUBE_NORMALS[FACE_POS_Z] = MatrixHelper.transformNormal(normalMatrix, true, Direction.SOUTH);
        CUBE_NORMALS[FACE_NEG_X] = MatrixHelper.transformNormal(normalMatrix, true, Direction.WEST);
        CUBE_NORMALS[FACE_POS_X] = MatrixHelper.transformNormal(normalMatrix, true, Direction.EAST);

        CUBE_NORMALS_MIRRORED[FACE_NEG_Y] = CUBE_NORMALS[FACE_NEG_Y];
        CUBE_NORMALS_MIRRORED[FACE_POS_Y] = CUBE_NORMALS[FACE_POS_Y];
        CUBE_NORMALS_MIRRORED[FACE_NEG_Z] = CUBE_NORMALS[FACE_NEG_Z];
        CUBE_NORMALS_MIRRORED[FACE_POS_Z] = CUBE_NORMALS[FACE_POS_Z];
        CUBE_NORMALS_MIRRORED[FACE_NEG_X] = CUBE_NORMALS[FACE_POS_X];
        CUBE_NORMALS_MIRRORED[FACE_POS_X] = CUBE_NORMALS[FACE_NEG_X];
    }

    private static void prepareVertices(PoseStack.Pose matrices, ModelCuboid cuboid) {
        updateCubeCorner(CUBE_CORNERS[VERTEX_X1_Y1_Z1], cuboid.x1, cuboid.y1, cuboid.z1, matrices.pose());
        updateCubeCorner(CUBE_CORNERS[VERTEX_X2_Y1_Z1], cuboid.x2, cuboid.y1, cuboid.z1, matrices.pose());
        updateCubeCorner(CUBE_CORNERS[VERTEX_X2_Y2_Z1], cuboid.x2, cuboid.y2, cuboid.z1, matrices.pose());
        updateCubeCorner(CUBE_CORNERS[VERTEX_X1_Y2_Z1], cuboid.x1, cuboid.y2, cuboid.z1, matrices.pose());
        updateCubeCorner(CUBE_CORNERS[VERTEX_X1_Y1_Z2], cuboid.x1, cuboid.y1, cuboid.z2, matrices.pose());
        updateCubeCorner(CUBE_CORNERS[VERTEX_X2_Y1_Z2], cuboid.x2, cuboid.y1, cuboid.z2, matrices.pose());
        updateCubeCorner(CUBE_CORNERS[VERTEX_X2_Y2_Z2], cuboid.x2, cuboid.y2, cuboid.z2, matrices.pose());
        updateCubeCorner(CUBE_CORNERS[VERTEX_X1_Y2_Z2], cuboid.x1, cuboid.y2, cuboid.z2, matrices.pose());

        updateTextureCoordinates(cuboid);
    }

    private static void updateTextureCoordinates(ModelCuboid cuboid) {
        buildVertexTexCoord(VERTEX_TEXTURES[FACE_NEG_Y], cuboid.u1, cuboid.v0, cuboid.u2, cuboid.v1);
        buildVertexTexCoord(VERTEX_TEXTURES[FACE_POS_Y], cuboid.u2, cuboid.v1, cuboid.u3, cuboid.v0);
        buildVertexTexCoord(VERTEX_TEXTURES[FACE_NEG_Z], cuboid.u1, cuboid.v1, cuboid.u2, cuboid.v2);
        buildVertexTexCoord(VERTEX_TEXTURES[FACE_POS_Z], cuboid.u4, cuboid.v1, cuboid.u5, cuboid.v2);
        buildVertexTexCoord(VERTEX_TEXTURES[FACE_NEG_X], cuboid.u2, cuboid.v1, cuboid.u4, cuboid.v2);
        buildVertexTexCoord(VERTEX_TEXTURES[FACE_POS_X], cuboid.u0, cuboid.v1, cuboid.u1, cuboid.v2);

        for (int face = 0; face < NUM_CUBE_FACES; face++) {
            for (int vert = 0; vert < NUM_FACE_VERTICES; vert++) {
                VERTEX_TEXTURES_MIRRORED[face][vert].set(VERTEX_TEXTURES[face][3 - vert]);
            }
        }
    }

    private static void updateCubeCorner(Vector3f vec, float x, float y, float z, Matrix4f matrix) {
        vec.set(
                MatrixHelper.transformPositionX(matrix, x, y, z),
                MatrixHelper.transformPositionY(matrix, x, y, z),
                MatrixHelper.transformPositionZ(matrix, x, y, z)
        );
    }

    private static void buildVertexTexCoord(Vector2f[] uvs, float u1, float v1, float u2, float v2) {
        uvs[0].set(u2, v1);
        uvs[1].set(u1, v1);
        uvs[2].set(u1, v2);
        uvs[3].set(u2, v2);
    }

    private static void renderChildren(PoseStack poseStack, VertexBufferWriter writer, ModelPart[] children, int light, int overlay, int color) {
        for (ModelPart child : children) {
            render(poseStack, writer, child, light, overlay, color);
        }
    }
}