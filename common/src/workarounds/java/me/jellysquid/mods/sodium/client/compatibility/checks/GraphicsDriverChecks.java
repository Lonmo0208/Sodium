package me.jellysquid.mods.sodium.client.compatibility.checks;

import me.jellysquid.mods.sodium.client.compatibility.environment.GLContextInfo;
import me.jellysquid.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterVendor;
import me.jellysquid.mods.sodium.client.compatibility.workarounds.intel.IntelWorkarounds;
import me.jellysquid.mods.sodium.client.compatibility.workarounds.nvidia.NvidiaDriverVersion;
import me.jellysquid.mods.sodium.client.compatibility.workarounds.nvidia.NvidiaWorkarounds;
import me.jellysquid.mods.sodium.client.platform.NativeWindowHandle;
import me.jellysquid.mods.sodium.client.platform.PlatformHelper;

class GraphicsDriverChecks {
    static void postContextInit(NativeWindowHandle window, GLContextInfo context) {
        var vendor = GraphicsAdapterVendor.fromContext(context);

        if (vendor == GraphicsAdapterVendor.UNKNOWN) {
            return;
        }

        if (vendor == GraphicsAdapterVendor.INTEL && BugChecks.ISSUE_899) {
            var installedVersion = IntelWorkarounds.findIntelDriverMatchingBug899();

            if (installedVersion != null) {
                var installedVersionString = installedVersion.toString();

                PlatformHelper.showCriticalErrorAndClose(window,
                        "Sodium Renderer - Unsupported Driver",
                        """
                                The game failed to start because the currently installed Intel Graphics Driver is not \
                                compatible.
                                
                                Installed version: ###CURRENT_DRIVER###
                                Required version: 10.18.10.5161 (or newer)
                                
                                Please click the 'Help' button to read more about how to fix this problem."""
                                .replace("###CURRENT_DRIVER###", installedVersionString),
                       "https://link.caffeinemc.net/help/sodium/graphics-driver/windows/intel/gh-899");
            }
        }

        if (vendor == GraphicsAdapterVendor.NVIDIA && BugChecks.ISSUE_1486) {
            var installedVersion = NvidiaWorkarounds.findNvidiaDriverMatchingBug1486();

            if (installedVersion != null) {
                var installedVersionString = NvidiaDriverVersion.parse(installedVersion)
                        .toString();

                PlatformHelper.showCriticalErrorAndClose(window,
                        "Sodium Renderer - Unsupported Driver",
                        """
                                The game failed to start because the currently installed NVIDIA Graphics Driver is not \
                                compatible.
                                
                                Installed version: ###CURRENT_DRIVER###
                                Required version: 536.23 (or newer)
                                
                                Please click the 'Help' button to read more about how to fix this problem."""
                                .replace("###CURRENT_DRIVER###", installedVersionString),
                        "https://link.caffeinemc.net/help/sodium/graphics-driver/windows/nvidia/gh-1486");

            }
        }
    }
}
