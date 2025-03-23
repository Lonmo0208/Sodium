package me.jellysquid.mods.sodium.client.render.frapi.helper;

import java.nio.ByteOrder;

public abstract class ColorHelper {
    private ColorHelper() { }

    private static final boolean BIG_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;

    public static int multiplyColor(int color1, int color2) {
        if (color1 == -1) return color2;
        if (color2 == -1) return color1;

        int alpha = ((color1 >>> 24) & 0xFF) * ((color2 >>> 24) & 0xFF) >>> 8;
        int red = ((color1 >>> 16) & 0xFF) * ((color2 >>> 16) & 0xFF) >>> 8;
        int green = ((color1 >>> 8) & 0xFF) * ((color2 >>> 8) & 0xFF) >>> 8;
        int blue = (color1 & 0xFF) * (color2 & 0xFF) >>> 8;

        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    public static int multiplyRGB(int color, float shade) {
        int alpha = color & 0xFF000000;
        int red = (int) ((color >> 16 & 0xFF) * shade) << 16;
        int green = (int) ((color >> 8 & 0xFF) * shade) << 8;
        int blue = (int) ((color & 0xFF) * shade);

        return alpha | red | green | blue;
    }

    public static int maxBrightness(int b0, int b1) {
        if (b0 == 0) return b1;
        if (b1 == 0) return b0;

        return Math.max(b0 & 0xFFFF, b1 & 0xFFFF) | Math.max(b0 & 0xFFFF0000, b1 & 0xFFFF0000);
    }

    public static int toVanillaColor(int color) {
        if (color == -1) return -1;

        if (BIG_ENDIAN) {
            return ((color & 0x00FFFFFF) << 8) | (color >>> 24);
        } else {
            return (color & 0xFF00FF00) | ((color & 0x00FF0000) >>> 16) | ((color & 0x000000FF) << 16);
        }
    }

    public static int fromVanillaColor(int color) {
        if (color == -1) return -1;

        if (BIG_ENDIAN) {
            return ((color & 0xFFFFFF00) >>> 8) | ((color & 0x000000FF) << 24);
        } else {
            return (color & 0xFF00FF00) | ((color & 0x00FF0000) >>> 16) | ((color & 0x000000FF) << 16);
        }
    }
}