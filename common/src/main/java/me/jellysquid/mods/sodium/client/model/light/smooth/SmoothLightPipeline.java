package me.jellysquid.mods.sodium.client.model.light.smooth;

import me.jellysquid.mods.sodium.client.model.light.LightPipeline;
import me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFlags;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

public class SmoothLightPipeline implements LightPipeline {
    private final LightDataAccess lightCache;
    private final AoFaceData[] cachedFaceData = new AoFaceData[6 * 2];
    private long cachedPos = Long.MIN_VALUE;
    private final float[] weights = new float[4];
    private final Vector3f vertexNormal = new Vector3f();
    private final AoFaceData tmpFace = new AoFaceData();

    public SmoothLightPipeline(LightDataAccess cache) {
        this.lightCache = cache;
        for (int i = 0; i < this.cachedFaceData.length; i++) {
            this.cachedFaceData[i] = new AoFaceData();
        }
    }

    @Override
    public void calculate(ModelQuadView quad, BlockPos pos, QuadLightData out, Direction cullFace, Direction lightFace, boolean shade, boolean isFluid) {
        this.updateCachedData(pos.asLong());

        int flags = quad.getFlags();
        final AoNeighborInfo neighborInfo = AoNeighborInfo.get(lightFace);

        if ((flags & ModelQuadFlags.IS_ALIGNED) != 0 || ((flags & ModelQuadFlags.IS_PARALLEL) != 0 && LightDataAccess.unpackFC(this.lightCache.get(pos)))) {
            if ((flags & ModelQuadFlags.IS_PARTIAL) == 0) {
                this.applyAlignedFullFace(neighborInfo, pos, lightFace, out, shade);
            } else {
                this.applyAlignedPartialFace(neighborInfo, quad, pos, lightFace, out, shade);
            }
        } else if ((flags & ModelQuadFlags.IS_PARALLEL) != 0) {
            this.applyParallelFace(neighborInfo, quad, pos, lightFace, out, shade);
        } else if (isFluid) {
            this.applyIrregularFace(pos, quad, out, shade);
        } else {
            this.applyNonParallelFace(neighborInfo, quad, pos, lightFace, out, shade);
        }
    }

    private void applyAlignedFullFace(AoNeighborInfo neighborInfo, BlockPos pos, Direction dir, QuadLightData out, boolean shade) {
        AoFaceData faceData = this.getCachedFaceData(pos, dir, true, shade);
        neighborInfo.mapCorners(faceData.lm, faceData.ao, out.lm, out.br);
    }

    private void applyAlignedPartialFace(AoNeighborInfo neighborInfo, ModelQuadView quad, BlockPos pos, Direction dir, QuadLightData out, boolean shade) {
        for (int i = 0; i < 4; i++) {
            float cx = clamp(quad.getX(i));
            float cy = clamp(quad.getY(i));
            float cz = clamp(quad.getZ(i));

            neighborInfo.calculateCornerWeights(cx, cy, cz, this.weights);
            this.applyAlignedPartialFaceVertex(pos, dir, this.weights, i, out, true, shade);
        }
    }

    private void applyParallelFace(AoNeighborInfo neighborInfo, ModelQuadView quad, BlockPos pos, Direction dir, QuadLightData out, boolean shade) {
        for (int i = 0; i < 4; i++) {
            float cx = clamp(quad.getX(i));
            float cy = clamp(quad.getY(i));
            float cz = clamp(quad.getZ(i));

            neighborInfo.calculateCornerWeights(cx, cy, cz, this.weights);
            float depth = neighborInfo.getDepth(cx, cy, cz);

            if (Mth.equal(depth, 1.0F)) {
                this.applyAlignedPartialFaceVertex(pos, dir, this.weights, i, out, false, shade);
            } else {
                this.applyInsetPartialFaceVertex(pos, dir, depth, 1.0f - depth, this.weights, i, out, shade);
            }
        }
    }

    private void applyNonParallelFace(AoNeighborInfo neighborInfo, ModelQuadView quad, BlockPos pos, Direction dir, QuadLightData out, boolean shade) {
        for (int i = 0; i < 4; i++) {
            float cx = clamp(quad.getX(i));
            float cy = clamp(quad.getY(i));
            float cz = clamp(quad.getZ(i));

            neighborInfo.calculateCornerWeights(cx, cy, cz, this.weights);
            float depth = neighborInfo.getDepth(cx, cy, cz);

            if (Mth.equal(depth, 0.0F)) {
                this.applyAlignedPartialFaceVertex(pos, dir, this.weights, i, out, true, shade);
            } else if (Mth.equal(depth, 1.0F)) {
                this.applyAlignedPartialFaceVertex(pos, dir, this.weights, i, out, false, shade);
            } else {
                this.applyInsetPartialFaceVertex(pos, dir, depth, 1.0f - depth, this.weights, i, out, shade);
            }
        }
    }

    private void applyAlignedPartialFaceVertex(BlockPos pos, Direction dir, float[] w, int i, QuadLightData out, boolean offset, boolean shade) {
        AoFaceData faceData = this.getCachedFaceData(pos, dir, offset, shade);

        if (faceData.hasUnpackedLightData()) {
            faceData.unpackLightData();
        }

        out.br[i] = faceData.getBlendedShade(w);
        out.lm[i] = getLightMapCoord(faceData.getBlendedSkyLight(w), faceData.getBlendedBlockLight(w));
    }

    private void applyInsetPartialFaceVertex(BlockPos pos, Direction dir, float n1d, float n2d, float[] w, int i, QuadLightData out, boolean shade) {
        AoFaceData n1 = this.getCachedFaceData(pos, dir, false, shade);
        AoFaceData n2 = this.getCachedFaceData(pos, dir, true, shade);

        if (n1.hasUnpackedLightData()) n1.unpackLightData();
        if (n2.hasUnpackedLightData()) n2.unpackLightData();

        float ao = (n1.getBlendedShade(w) * n1d) + (n2.getBlendedShade(w) * n2d);
        float sl = (n1.getBlendedSkyLight(w) * n1d) + (n2.getBlendedSkyLight(w) * n2d);
        float bl = (n1.getBlendedBlockLight(w) * n1d) + (n2.getBlendedBlockLight(w) * n2d);

        out.br[i] = ao;
        out.lm[i] = getLightMapCoord(sl, bl);
    }

    private void applyIrregularFace(BlockPos blockPos, ModelQuadView quad, QuadLightData out, boolean shade) {
        final float[] w = this.weights;
        final float[] aoResult = out.br;
        final int[] lightResult = out.lm;

        for (int i = 0; i < 4; i++) {
            Vector3f normal = NormI8.unpack(quad.getAccurateNormal(i), vertexNormal);

            float[] axisResults = new float[6]; // [ao, sky, block, maxAo, maxSky, maxBlock]

            // 处理每个轴前，先clamp坐标
            float x = clamp(quad.getX(i));
            float y = clamp(quad.getY(i));
            float z = clamp(quad.getZ(i));

            processAxis(normal.x(), x, y, z, Direction.EAST, Direction.WEST, blockPos, i, w, shade, axisResults);
            processAxis(normal.y(), x, y, z, Direction.UP, Direction.DOWN, blockPos, i, w, shade, axisResults);
            processAxis(normal.z(), x, y, z, Direction.SOUTH, Direction.NORTH, blockPos, i, w, shade, axisResults);

            aoResult[i] = (axisResults[0] + axisResults[3]) * 0.5f;
            lightResult[i] = getLightMapCoord(
                    (axisResults[1] + axisResults[4]) * 0.5f,
                    (axisResults[2] + axisResults[5]) * 0.5f
            );
        }
    }

    private void processAxis(float axisValue, float x, float y, float z, Direction positiveDir, Direction negativeDir,
                             BlockPos pos, int vertexIdx, float[] w, boolean shade, float[] results) {
        if (!Mth.equal(0f, axisValue)) {
            Direction face = axisValue > 0 ? positiveDir : negativeDir;
            AoFaceData fd = gatherInsetFace(pos, vertexIdx, face, shade, x, y, z);
            AoNeighborInfo.get(face).calculateCornerWeights(x, y, z, w);

            float n = axisValue * axisValue;
            float a = fd.getBlendedShade(w);
            float s = fd.getBlendedSkyLight(w);
            float b = fd.getBlendedBlockLight(w);

            results[0] += n * a;
            results[1] += n * s;
            results[2] += n * b;
            results[3] = Math.max(results[3], a);
            results[4] = Math.max(results[4], s);
            results[5] = Math.max(results[5], b);
        }
    }

    private AoFaceData gatherInsetFace(BlockPos pos, int vertexIdx, Direction face, boolean shade, float x, float y, float z) {
        float depth = AoNeighborInfo.get(face).getDepth(x, y, z);

        if (Mth.equal(depth, 0)) return getCachedFaceData(pos, face, true, shade);
        if (Mth.equal(depth, 1)) return getCachedFaceData(pos, face, false, shade);

        tmpFace.reset();
        return AoFaceData.weightedMean(
                getCachedFaceData(pos, face, true, shade), 1 - depth,
                getCachedFaceData(pos, face, false, shade), depth,
                tmpFace
        );
    }

    private AoFaceData getCachedFaceData(BlockPos pos, Direction face, boolean offset, boolean shade) {
        AoFaceData data = this.cachedFaceData[offset ? face.ordinal() : face.ordinal() + 6];

        if (!data.hasLightData()) {
            data.initLightData(this.lightCache, pos, face, offset);
            applySidedBrightness(data, face, shade);
            data.unpackLightData();
        }

        return data;
    }

    private void applySidedBrightness(AoFaceData data, Direction face, boolean shade) {
        float brightness = this.lightCache.getLevel().getShade(face, shade);
        for (int i = 0; i < data.ao.length; i++) {
            data.ao[i] *= brightness;
        }
    }

    private void updateCachedData(long key) {
        if (this.cachedPos != key) {
            for (AoFaceData data : this.cachedFaceData) data.reset();
            this.cachedPos = key;
        }
    }

    private static float clamp(float v) {
        return Mth.clamp(v, 0.0f, 1.0f);
    }

    private static int getLightMapCoord(float sl, float bl) {
        return (((int) sl & 0xFF) << 16) | ((int) bl & 0xFF);
    }
}