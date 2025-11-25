package net.caffeinemc.mods.sodium.api.config.structure;

import net.minecraft.resources.Identifier;

/**
 * Builder interface for defining option overrides, which replace an existing option with a new one.
 */
public interface OptionOverrideBuilder {
    OptionOverrideBuilder setTarget(Identifier target);

    /**
     * Sets the replacement option.
     *
     * @param option The option builder for the replacement option.
     * @return The current builder instance.
     */
    OptionOverrideBuilder setReplacement(OptionBuilder option);
}
