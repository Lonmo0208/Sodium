package net.caffeinemc.mods.sodium.mixin.workarounds.loading_race;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {
    @Shadow
    private boolean clientLoaded;

    @Inject(method = "hasClientLoaded", at = @At("HEAD"), cancellable = true)
    private void hasClientLoaded(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(this.clientLoaded);
    }
}
