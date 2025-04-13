package net.caffeinemc.mods.sodium.mixin.workarounds.loading_race;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.LevelLoadStatusManager;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin extends ClientCommonPacketListenerImpl {
    @Shadow
    @Nullable
    private LevelLoadStatusManager levelLoadStatusManager;

    protected ClientPacketListenerMixin(Minecraft minecraft, Connection connection, CommonListenerCookie commonListenerCookie) {
        super(minecraft, connection, commonListenerCookie);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/LevelLoadStatusManager.levelReady()Z"))
    private void tick(CallbackInfo ci) {
        if (this.levelLoadStatusManager != null && this.minecraft.player != null) {
            if (this.levelLoadStatusManager.levelReady() && !this.minecraft.player.hasClientLoaded() && ((PlayerAccessor) this.minecraft.player).getClientLoadedTimeoutTimer() <= 0) {
                this.connection.send(new ServerboundPlayerLoadedPacket());
                this.minecraft.player.setClientLoaded(true);
            }
        }
    }
}
