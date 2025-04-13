package net.caffeinemc.mods.sodium.mixin.workarounds.loading_race;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Player.class)
public interface PlayerAccessor {
    @Accessor
    int getClientLoadedTimeoutTimer();
}
