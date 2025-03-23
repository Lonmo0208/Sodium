package me.jellysquid.mods.sodium.client.render.immediate;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.api.util.ColorMixer;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ColorVertex;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class CloudRenderer {
    private static final ResourceLocation CLOUDS_TEXTURE_ID = new ResourceLocation("textures/environment/clouds.png");

    private CloudTextureData textureData;
    private ShaderInstance shaderProgram;

    private @Nullable CloudRenderer.CloudGeometry cachedGeometry;

    public CloudRenderer(ResourceProvider resourceProvider) {
        this.reloadTextures(resourceProvider);
    }

    public void render(Camera camera,
                       ClientLevel level,
                       Matrix4f projectionMatrix,
                       PoseStack poseStack,
                       float ticks,
                       float tickDelta)
    {
        float cloudHeight = level.effects().getCloudHeight();
        if (Float.isNaN(cloudHeight) || this.textureData.isBlank) return;

        Vec3 pos = camera.getPosition();
        double cloudTime = (ticks + tickDelta) * 0.03F;
        int cloudDistance = getCloudRenderDistance();

        int centerCellX = (int) Math.floor((pos.x() + cloudTime) / 12.0);
        int centerCellZ = (int) Math.floor((pos.z() + 0.33D) / 12.0);

        int orientation = (int) Math.signum(pos.y() - cloudHeight);
        var parameters = new CloudGeometryParameters(centerCellX, centerCellZ, cloudDistance, orientation, Minecraft.getInstance().options.getCloudsType());

        CloudGeometry geometry = this.cachedGeometry;
        if (geometry == null || !Objects.equals(geometry.params(), parameters)) {
            this.cachedGeometry = (geometry = rebuildGeometry(geometry, parameters, this.textureData));
        }

        poseStack.pushPose();
        var poseEntry = poseStack.last();
        Matrix4f modelViewMatrix = poseEntry.pose();

        float translateX = (float) ((pos.x() + cloudTime) - (centerCellX * 12));
        float translateZ = (float) ((pos.z() + 0.33D) - (centerCellZ * 12));
        modelViewMatrix.translate(-translateX, cloudHeight - (float) pos.y() + 0.33F, -translateZ);

        final var prevShaderFogShape = RenderSystem.getShaderFogShape();
        final var prevShaderFogEnd = RenderSystem.getShaderFogEnd();
        final var prevShaderFogStart = RenderSystem.getShaderFogStart();
        FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_TERRAIN, cloudDistance * 8, shouldUseWorldFog(level, pos), tickDelta);

        boolean fastClouds = geometry.params().renderMode() == CloudStatus.FAST;
        boolean fabulous = Minecraft.useShaderTransparency();

        if (fastClouds) RenderSystem.disableCull();
        if (fabulous) Minecraft.getInstance().levelRenderer.getCloudsTarget().bindWrite(false);

        Vec3 colorModulator = level.getCloudColor(tickDelta);
        RenderSystem.setShaderColor((float) colorModulator.x, (float) colorModulator.y, (float) colorModulator.z, 0.8f);

        VertexBuffer vertexBuffer = geometry.vertexBuffer();
        vertexBuffer.bind();

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthFunc(GL32C.GL_LESS);

        vertexBuffer.drawWithShader(modelViewMatrix, projectionMatrix, this.shaderProgram);

        RenderSystem.depthFunc(GL32C.GL_LEQUAL);
        RenderSystem.disableBlend();
        VertexBuffer.unbind();

        if (fastClouds) RenderSystem.enableCull();
        if (fabulous) Minecraft.getInstance().getMainRenderTarget().bindWrite(false);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderFogShape(prevShaderFogShape);
        RenderSystem.setShaderFogEnd(prevShaderFogEnd);
        RenderSystem.setShaderFogStart(prevShaderFogStart);
        poseStack.popPose();
    }

    private static @NotNull CloudGeometry rebuildGeometry(@Nullable CloudGeometry existingGeometry,
                                                          CloudGeometryParameters parameters,
                                                          CloudTextureData textureData)
    {
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        var writer = VertexBufferWriter.of(bufferBuilder);
        int radius = parameters.radius();
        boolean useFastGraphics = parameters.renderMode() == CloudStatus.FAST;

        addCellGeometryToBuffer(writer, textureData, parameters.originX(), parameters.originZ(), 0, 0, parameters.orientation(), useFastGraphics);

        for (int layer = 1; layer <= radius; layer++) {
            for (int z = -layer; z < layer; z++) {
                int x = Math.abs(z) - layer;
                addCellGeometryToBuffer(writer, textureData, parameters.originX(), parameters.originZ(), x, z, parameters.orientation(), useFastGraphics);
            }

            for (int z = layer; z > -layer; z--) {
                int x = layer - Math.abs(z);
                addCellGeometryToBuffer(writer, textureData, parameters.originX(), parameters.originZ(), x, z, parameters.orientation(), useFastGraphics);
            }
        }

        for (int layer = radius + 1; layer <= 2 * radius; layer++) {
            int l = layer - radius;

            for (int z = -radius; z <= -l; z++) {
                addCellGeometryToBuffer(writer, textureData, parameters.originX(), parameters.originZ(), -z - layer, z, parameters.orientation(), useFastGraphics);
            }

            for (int z = l; z <= radius; z++) {
                addCellGeometryToBuffer(writer, textureData, parameters.originX(), parameters.originZ(), z - layer, z, parameters.orientation(), useFastGraphics);
            }

            for (int z = radius; z >= l; z--) {
                addCellGeometryToBuffer(writer, textureData, parameters.originX(), parameters.originZ(), layer - z, z, parameters.orientation(), useFastGraphics);
            }

            for (int z = -l; z >= -radius; z--) {
                addCellGeometryToBuffer(writer, textureData, parameters.originX(), parameters.originZ(), layer + z, z, parameters.orientation(), useFastGraphics);
            }
        }

        BufferBuilder.RenderedBuffer builtBuffer = bufferBuilder.end();
        VertexBuffer vertexBuffer = existingGeometry != null ? existingGeometry.vertexBuffer() : new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        uploadToVertexBuffer(vertexBuffer, builtBuffer);

        return new CloudGeometry(vertexBuffer, parameters);
    }

    private static void addCellGeometryToBuffer(VertexBufferWriter writer,
                                                CloudTextureData textureData,
                                                int originX,
                                                int originZ,
                                                int offsetX,
                                                int offsetZ,
                                                int orientation,
                                                boolean useFastGraphics) {
        int cellX = originX + offsetX;
        int cellZ = originZ + offsetZ;

        int cellIndex = textureData.getCellIndexWrapping(cellX, cellZ);
        int cellFaces = textureData.getCellFaces(cellIndex) & getVisibleFaces(offsetX, offsetZ, orientation);

        if (cellFaces == 0) return;

        int cellColor = textureData.getCellColor(cellIndex);
        float x = offsetX * 12;
        float z = offsetZ * 12;

        if (useFastGraphics) {
            emitCellGeometry2D(writer, cellFaces, cellColor, x, z);
        } else {
            emitCellGeometry3D(writer, cellFaces, cellColor, x, z, false);
            if (Math.abs(offsetX) + Math.abs(offsetZ) <= 1) {
                emitCellGeometry3D(writer, CloudFaceSet.all(), cellColor, x, z, true);
            }
        }
    }

    private static int getVisibleFaces(int x, int z, int orientation) {
        int faces = CloudFaceSet.all();

        if (x > 0) faces = CloudFaceSet.remove(faces, CloudFace.POS_X);
        if (z > 0) faces = CloudFaceSet.remove(faces, CloudFace.POS_Z);
        if (x < 0) faces = CloudFaceSet.remove(faces, CloudFace.NEG_X);
        if (z < 0) faces = CloudFaceSet.remove(faces, CloudFace.NEG_Z);
        if (orientation < 0) faces = CloudFaceSet.remove(faces, CloudFace.POS_Y);
        if (orientation > 0) faces = CloudFaceSet.remove(faces, CloudFace.NEG_Y);

        return faces;
    }

    private static final Vector3f[][] VERTICES = new Vector3f[CloudFace.COUNT][];

    static {
        VERTICES[CloudFace.NEG_Y.ordinal()] = new Vector3f[] {
                new Vector3f(12.0f, 0.0f, 12.0f),
                new Vector3f( 0.0f, 0.0f, 12.0f),
                new Vector3f( 0.0f, 0.0f,  0.0f),
                new Vector3f(12.0f, 0.0f,  0.0f)
        };

        VERTICES[CloudFace.POS_Y.ordinal()] = new Vector3f[] {
                new Vector3f( 0.0f, 4.0f, 12.0f),
                new Vector3f(12.0f, 4.0f, 12.0f),
                new Vector3f(12.0f, 4.0f,  0.0f),
                new Vector3f( 0.0f, 4.0f,  0.0f)
        };

        VERTICES[CloudFace.NEG_X.ordinal()] = new Vector3f[] {
                new Vector3f( 0.0f, 0.0f, 12.0f),
                new Vector3f( 0.0f, 4.0f, 12.0f),
                new Vector3f( 0.0f, 4.0f,  0.0f),
                new Vector3f( 0.0f, 0.0f,  0.0f)
        };

        VERTICES[CloudFace.POS_X.ordinal()] = new Vector3f[] {
                new Vector3f(12.0f, 4.0f, 12.0f),
                new Vector3f(12.0f, 0.0f, 12.0f),
                new Vector3f(12.0f, 0.0f,  0.0f),
                new Vector3f(12.0f, 4.0f,  0.0f)
        };

        VERTICES[CloudFace.NEG_Z.ordinal()] = new Vector3f[] {
                new Vector3f(12.0f, 4.0f,  0.0f),
                new Vector3f(12.0f, 0.0f,  0.0f),
                new Vector3f( 0.0f, 0.0f,  0.0f),
                new Vector3f( 0.0f, 4.0f,  0.0f)
        };

        VERTICES[CloudFace.POS_Z.ordinal()] = new Vector3f[] {
                new Vector3f(12.0f, 0.0f, 12.0f),
                new Vector3f(12.0f, 4.0f, 12.0f),
                new Vector3f( 0.0f, 4.0f, 12.0f),
                new Vector3f( 0.0f, 0.0f, 12.0f)
        };
    }

    private static void emitCellGeometry2D(VertexBufferWriter writer, int faces, int color, float x, float z) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long buffer = stack.nmalloc(4 * ColorVertex.STRIDE);
            long ptr = buffer;

            int mixedColor = ColorMixer.mul(color, CloudFace.POS_Y.getColor());
            for (int i = 0; i < 4; i++) {
                float px = x + (i == 0 || i == 3 ? 12.0f : 0.0f);
                float pz = z + (i < 2 ? 12.0f : 0.0f);
                ptr = writeVertex(ptr, px, 0.0f, pz, mixedColor);
            }
            writer.push(stack, buffer, 4, ColorVertex.FORMAT);
        }
    }

    private static void emitCellGeometry3D(VertexBufferWriter writer, int visibleFaces, int baseColor, float posX, float posZ, boolean interior) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long buffer = stack.nmalloc(6 * 4 * ColorVertex.STRIDE);
            long ptr = buffer;
            int count = 0;

            for (var face : CloudFace.VALUES) {
                if (!CloudFaceSet.contains(visibleFaces, face)) continue;

                final var vertices = VERTICES[face.ordinal()];
                final int color = ColorMixer.mul(baseColor, face.getColor());

                for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
                    Vector3f vertex = vertices[interior ? 3 - vertexIndex : vertexIndex];
                    ptr = writeVertex(ptr, vertex.x + posX, vertex.y, vertex.z + posZ, color);
                }
                count += 4;
            }

            if (count > 0) writer.push(stack, buffer, count, ColorVertex.FORMAT);
        }
    }

    private static long writeVertex(long buffer, float x, float y, float z, int color) {
        ColorVertex.put(buffer, x, y, z, color);
        return buffer + ColorVertex.STRIDE;
    }

    private static void uploadToVertexBuffer(VertexBuffer vertexBuffer, BufferBuilder.RenderedBuffer builtBuffer) {
        vertexBuffer.bind();
        vertexBuffer.upload(builtBuffer);
        VertexBuffer.unbind();
    }

    public void reloadTextures(ResourceProvider resourceProvider) {
        this.destroy();
        this.textureData = loadTextureData();

        try {
            this.shaderProgram = new ShaderInstance(resourceProvider, "clouds", DefaultVertexFormat.POSITION_COLOR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void destroy() {
        if (this.shaderProgram != null) {
            this.shaderProgram.close();
            this.shaderProgram = null;
        }

        if (this.cachedGeometry != null) {
            this.cachedGeometry.vertexBuffer().close();
            this.cachedGeometry = null;
        }
    }

    private static CloudTextureData loadTextureData() {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        Resource resource = resourceManager.getResource(CLOUDS_TEXTURE_ID).orElseThrow();

        try (InputStream inputStream = resource.open(); NativeImage nativeImage = NativeImage.read(inputStream)) {
            return new CloudTextureData(nativeImage);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load texture data", ex);
        }
    }

    private static boolean shouldUseWorldFog(ClientLevel level, Vec3 pos) {
        return level.effects().isFoggyAt(Mth.floor(pos.x()), Mth.floor(pos.z())) ||
                Minecraft.getInstance().gui.getBossOverlay().shouldCreateWorldFog();
    }

    private static int getCloudRenderDistance() {
        return Math.max(32, (Minecraft.getInstance().options.getEffectiveRenderDistance() * 2) + 9);
    }

    private enum CloudFace {
        NEG_Y(ColorABGR.pack(0.7F, 0.7F, 0.7F, 1.0f)),
        POS_Y(ColorABGR.pack(1.0f, 1.0f, 1.0f, 1.0f)),
        NEG_X(ColorABGR.pack(0.9F, 0.9F, 0.9F, 1.0f)),
        POS_X(ColorABGR.pack(0.9F, 0.9F, 0.9F, 1.0f)),
        NEG_Z(ColorABGR.pack(0.8F, 0.8F, 0.8F, 1.0f)),
        POS_Z(ColorABGR.pack(0.8F, 0.8F, 0.8F, 1.0f));

        public static final CloudFace[] VALUES = CloudFace.values();
        public static final int COUNT = VALUES.length;

        private final int color;

        CloudFace(int color) {
            this.color = color;
        }

        public int getColor() {
            return this.color;
        }
    }

    private static class CloudFaceSet {
        public static int empty() {
            return 0;
        }

        public static boolean contains(int set, CloudFace face) {
            return (set & (1 << face.ordinal())) != 0;
        }

        public static int add(int set, CloudFace face) {
            return set | (1 << face.ordinal());
        }

        public static int remove(int set, CloudFace face) {
            return set & ~(1 << face.ordinal());
        }

        public static int all() {
            return (1 << CloudFace.COUNT) - 1;
        }
    }

    private static class CloudTextureData {
        private final byte[] faces;
        private final int[] colors;
        private final boolean isBlank;
        private final int width, height;

        public CloudTextureData(NativeImage texture) {
            this.width = texture.getWidth();
            this.height = texture.getHeight();
            this.faces = new byte[this.width * this.height];
            this.colors = new int[this.width * this.height];
            this.isBlank = loadTextureData(texture);
        }

        private boolean loadTextureData(NativeImage texture) {
            boolean blank = true;
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < height; z++) {
                    int index = getCellIndex(x, z);
                    int color = texture.getPixelRGBA(x, z);
                    colors[index] = color;

                    if (ColorARGB.unpackAlpha(color) > 1) {
                        faces[index] = (byte) getOpenFaces(texture, color, x, z);
                        blank = false;
                    }
                }
            }
            return blank;
        }

        private static int getOpenFaces(NativeImage image, int color, int x, int z) {
            int faces = CloudFaceSet.add(CloudFaceSet.empty(), CloudFace.NEG_Y);
            faces = CloudFaceSet.add(faces, CloudFace.POS_Y);

            if (color != getNeighborTexel(image, x - 1, z)) faces = CloudFaceSet.add(faces, CloudFace.NEG_X);
            if (color != getNeighborTexel(image, x + 1, z)) faces = CloudFaceSet.add(faces, CloudFace.POS_X);
            if (color != getNeighborTexel(image, x, z - 1)) faces = CloudFaceSet.add(faces, CloudFace.NEG_Z);
            if (color != getNeighborTexel(image, x, z + 1)) faces = CloudFaceSet.add(faces, CloudFace.POS_Z);

            return faces;
        }

        private static int getNeighborTexel(NativeImage image, int x, int z) {
            x = wrapTexelCoord(x, 0, image.getWidth() - 1);
            z = wrapTexelCoord(z, 0, image.getHeight() - 1);
            return image.getPixelRGBA(x, z);
        }

        private static int wrapTexelCoord(int coord, int min, int max) {
            return coord < min ? max : coord > max ? min : coord;
        }

        public int getCellFaces(int index) {
            return this.faces[index];
        }

        public int getCellColor(int index) {
            return this.colors[index];
        }

        private int getCellIndexWrapping(int x, int z) {
            return getCellIndex(Math.floorMod(x, width), Math.floorMod(z, height));
        }

        private int getCellIndex(int x, int z) {
            return x * width + z;
        }
    }

    public record CloudGeometry(VertexBuffer vertexBuffer, CloudGeometryParameters params) {}

    public record CloudGeometryParameters(int originX, int originZ, int radius, int orientation, CloudStatus renderMode) {}
}
