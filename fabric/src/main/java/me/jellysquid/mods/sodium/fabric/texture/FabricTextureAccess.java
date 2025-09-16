package me.jellysquid.mods.sodium.fabric.texture;

import me.jellysquid.mods.sodium.client.services.PlatformTextureAccess;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public class FabricTextureAccess implements PlatformTextureAccess {
    @Override
    public TextureAtlasSprite findInBlockAtlas(float texU, float texV) {
        SpriteFinder finder = SpriteFinderCache.forBlockAtlas();
        if (finder == null) {
            return null;
        }
        return finder.find(texU, texV);
    }
}