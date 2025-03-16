package me.jellysquid.mods.sodium.fabric;

import me.jellysquid.mods.sodium.client.compatibility.checks.PreLaunchChecks;
import me.jellysquid.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterProbe;
import me.jellysquid.mods.sodium.client.compatibility.workarounds.Workarounds;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class SodiumPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        PreLaunchChecks.beforeLWJGLInit();
        GraphicsAdapterProbe.findAdapters();
        PreLaunchChecks.onGameInit();
        Workarounds.init();
    }
}
