package me.jellysquid.mods.sodium.client.gl.device;

import me.jellysquid.mods.sodium.client.gl.functions.DeviceFunctions;
import org.lwjgl.opengl.GLCapabilities;

public interface RenderDevice {

    static void enterManagedCode() {
        RenderDevice.INSTANCE.makeActive();
    }

    static void exitManagedCode() {
        RenderDevice.INSTANCE.makeInactive();
    }

    RenderDevice INSTANCE = new GLRenderDevice();

    CommandList createCommandList();

    void makeActive();
    void makeInactive();

    int getSubTexelPrecisionBits();
    GLCapabilities getCapabilities();
    DeviceFunctions getDeviceFunctions();
}