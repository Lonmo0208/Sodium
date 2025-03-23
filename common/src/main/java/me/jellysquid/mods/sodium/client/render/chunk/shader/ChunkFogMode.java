package me.jellysquid.mods.sodium.client.render.chunk.shader;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public enum ChunkFogMode {
    NONE(ChunkShaderFogComponent.None::new, ImmutableSet.of()),
    SMOOTH(ChunkShaderFogComponent.Smooth::new, ImmutableSet.of("USE_FOG", "USE_FOG_SMOOTH"));

    private final Function<ShaderBindingContext, ChunkShaderFogComponent> factory;
    private final Set<String> defines;

    ChunkFogMode(Function<ShaderBindingContext, ChunkShaderFogComponent> factory, Set<String> defines) {
        this.factory = factory;
        this.defines = defines;
    }

    public Function<ShaderBindingContext, ChunkShaderFogComponent> getFactory() {
        return this.factory;
    }

    public List<String> getDefines() {
        return ImmutableList.copyOf(this.defines); // 高效转换为 List
    }
}