package me.jellysquid.mods.sodium.service;

import me.jellysquid.mods.sodium.client.compatibility.checks.PreLaunchChecks;
import me.jellysquid.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterProbe;
import me.jellysquid.mods.sodium.client.compatibility.workarounds.Workarounds;
import me.jellysquid.mods.sodium.client.compatibility.workarounds.nvidia.NvidiaWorkarounds;
import net.minecraftforgespi.earlywindow.GraphicsBootstrapper;

public class SodiumWorkarounds implements GraphicsBootstrapper {
    @Override
    public String name() {
        return "sodium";
    }

    @Override
    public void bootstrap(String[] arguments) {
        GraphicsAdapterProbe.findAdapters();
        PreLaunchChecks.beforeLWJGLInit();
        PreLaunchChecks.onGameInit();
        Workarounds.init();
        final boolean applyNvidiaWorkarounds = Workarounds.isWorkaroundEnabled(Workarounds.Reference.NVIDIA_THREADED_OPTIMIZATIONS_BROKEN);

        if (applyNvidiaWorkarounds) {
            System.out.println("[Sodium] Applying NVIDIA workarounds earlier on Forge.");
            NvidiaWorkarounds.applyEnvironmentChanges();
        }
    }
}
