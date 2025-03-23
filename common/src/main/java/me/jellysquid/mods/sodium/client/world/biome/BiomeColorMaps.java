package me.jellysquid.mods.sodium.client.world.biome;

import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;

public class BiomeColorMaps {
    private static final int WIDTH = 256;
    private static final int HEIGHT = 256;
    private static final int INVALID_INDEX = -1;
    private static final int DEFAULT_COLOR = 0xffff00ff;

    public static int getGrassColor(int index) {
        return (index == INVALID_INDEX || index >= GrassColor.pixels.length) ? DEFAULT_COLOR : GrassColor.pixels[index];
    }

    public static int getFoliageColor(int index) {
        return (index == INVALID_INDEX || index >= FoliageColor.pixels.length) ? DEFAULT_COLOR : FoliageColor.pixels[index];
    }

    public static int getIndex(double temperature, double humidity) {
        humidity *= temperature;
        int x = (int) ((1.0D - temperature) * 255.0D);
        int y = (int) ((1.0D - humidity) * 255.0D);

        return (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) ? INVALID_INDEX : (y << 8) | x;
    }
}