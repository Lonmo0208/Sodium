package net.caffeinemc.mods.sodium.client.gl.arena;

import net.caffeinemc.mods.sodium.client.gl.buffer.GlBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;

public interface AllocatorBase {
    long getDeviceUsedMemory();

    long getDeviceAllocatedMemory();

    void free(GlBufferSegment entry);

    void deleteSingleOwner(CommandList commands);

    boolean isEmpty();

    GlBuffer getBufferObject();
}
