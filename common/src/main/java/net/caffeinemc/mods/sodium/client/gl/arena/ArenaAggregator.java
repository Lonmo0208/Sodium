package net.caffeinemc.mods.sodium.client.gl.arena;

import net.caffeinemc.mods.sodium.client.gl.arena.staging.StagingBuffer;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferUsage;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlMutableBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

// TODO: if the required capacity is huge, maybe it shouldn't be shared, or we should overshoot it more?
// TODO: when moving region to a new buffer, return the next shared arena, or decide that the request is too big and return a regular single-owner
// TODO: allow user to change the vram pre-allocation size via config, but auto-scale regardless if it's too small
// TODO: compaction when multiple shared arenas together have less than 70% of a single one used
// TODO: deallocate shared arenas when they become empty, don't re-use more than some limited amount of memory when deallocated shared arena becomes freed
public class ArenaAggregator {
    // how much bigger than requested a buffer can be to be considered for reuse
    public static final float MAX_BUFFER_REUSE_SIZE_FACTOR = 1.4f;
    private static final long SHARED_GEOMETRY_SIZE = MathUtil.fromMib(256);
    private static final long SHARED_INDEX_SIZE = MathUtil.fromMib(32);
    private static final int DEFRAG_COPIES_PER_FRAME_BUDGET = 32;
    private static final long DEFRAG_BYTES_PER_FRAME_BUDGET = MathUtil.fromMib(32);
    private static final float MIN_FREE_FRACTION_AFTER_DEALLOC = 0.2f;
    private static final float FREE_FRACTION_AFTER_DEALLOC_ABORT_LIMIT = 0.1f;

    private static final GlBufferUsage BUFFER_USAGE = GlBufferUsage.STATIC_DRAW;

    final StagingBuffer stagingBuffer;
    private final GlMutableBuffer[] freeBuffers = new GlMutableBuffer[8];
    private static int freeBufferCount = 0;

    // all shared arenas, keyed by stride, and then sorted by the biggest contiguous free block size they have to offer
    private DataType index = new DataType("Index", Integer.BYTES, SHARED_INDEX_SIZE);
    private final DataType geometry = new DataType("Geometry", ChunkMeshFormats.COMPACT.getVertexFormat().getStride(), SHARED_GEOMETRY_SIZE);
    private List<DataType> dataTypes = List.of(this.index, this.geometry);
    private int arenaDefragOffset = 0; // round-robin index for defragmentation
    private int totalCopyCount = 0;
    private long totalCopyBytes = 0;

    public static class DefragBudget {
        private final int startCopyCount;
        private final long startCopyBytes;
        private int copyCount;
        private long copyBytes;
        private long copyElements;

        public DefragBudget(int copyCount, long copyBytes) {
            this.startCopyCount = copyCount;
            this.startCopyBytes = copyBytes;
            this.copyCount = copyCount;
            this.copyBytes = copyBytes;
        }

        public void setupElementCopy(int elementSize) {
            this.copyElements = this.copyBytes / elementSize;
        }

        public void consumeElementCopy(long elementsCopied, long bytesCopied) {
            this.copyCount--;
            this.copyBytes -= bytesCopied;
            this.copyElements -= elementsCopied;
        }

        public boolean isElementBudgetEmpty() {
            return this.copyCount <= 0 || this.copyBytes <= 0 || this.copyElements <= 0;
        }

        public boolean isBudgetEmpty() {
            return this.copyCount <= 0 || this.copyBytes <= 0;
        }

        public boolean elementCopyExceedsBudget(long elements) {
            return elements > this.copyElements;
        }

        public int getUsedCopyCount() {
            return this.startCopyCount - this.copyCount;
        }

        public long getUsedCopyBytes() {
            return this.startCopyBytes - this.copyBytes;
        }

        public long getRemainingCopyBytes() {
            return this.copyBytes;
        }
    }

    private class DataType {
        final String name;
        final int stride;
        final long sharedSizeBytes;
        final long sharedSize;
        final ArrayList<SharedGlBufferArena> arenas;

        DataType(String name, int stride, long sharedSizeBytes) {
            this.name = name;
            this.stride = stride;
            this.sharedSizeBytes = sharedSizeBytes;
            this.sharedSize = sharedSizeBytes / stride;
            this.arenas = new ArrayList<>();
        }

        SharedGlBufferArena createSharedArena(CommandList commands, long requiredCapacity) {
            GlMutableBuffer buffer = ArenaAggregator.this.getBufferOfSizeAtLeast(commands, requiredCapacity * this.stride);
            long actualCapacity = buffer.getSize() / this.stride;
            return new SharedGlBufferArena(ArenaAggregator.this, buffer, actualCapacity, this.stride);
        }

        SharedGlBufferArena ensureSharedArena(CommandList commands, long requiredCapacity) {
            SharedGlBufferArena bestArena = null;
            long biggestFreeSegmentSize = requiredCapacity;
            for (var arena : this.arenas) {
                long arenaBiggestFreeSegmentSize = arena.getBiggestFreeSegmentSize();
                if (!arena.isEmptying() && arenaBiggestFreeSegmentSize >= biggestFreeSegmentSize) {
                    bestArena = arena;
                    biggestFreeSegmentSize = arenaBiggestFreeSegmentSize;
                }
            }

            if (bestArena == null) {
                bestArena = createSharedArena(commands, Math.max(requiredCapacity, this.sharedSize));
                this.arenas.add(bestArena);
            }

            return bestArena;
        }

        long getDeviceUsedMemory() {
            long used = 0;
            for (var arenaEntry : this.arenas) {
                used += arenaEntry.getDeviceUsedMemory();
            }
            return used;
        }

        long getDeviceAllocatedMemory() {
            long allocated = 0;
            for (var arenaEntry : this.arenas) {
                allocated += arenaEntry.getDeviceAllocatedMemory();
            }
            return allocated;
        }

        void update(CommandList commands, DefragBudget budget) {
            budget.setupElementCopy(this.stride);

            // calculate total unfragmented free and capacity, remove empty arenas.
            // note that this uses unfragmented free instead of calculating total free from total usage and total capacity because if we do this with fragmented free it might try to deallocate and arena that requires moving data but can't because there's not enough contiguous free space in the other arenas.
            long totalCapacity = 0;
            long totalUnfragmentedFree = 0;
            SharedGlBufferArena emptyingArena = null;
            var it = this.arenas.iterator();
            while (it.hasNext()) {
                var arena = it.next();

                if (arena.isEmpty()) {
                    arena.deleteShared(commands);
                    it.remove();
                    continue;
                }

                totalCapacity += arena.getCapacity();
                totalUnfragmentedFree += arena.getBiggestFreeSegmentSize();
                if (arena.isEmptying()) {
                    emptyingArena = arena;
                }
            }

            // perform emptying on the currently emptying arena
            if (emptyingArena != null) {
                // make sure the arena that's emptying wouldn't cause there to be too little free space
                if (emptyingArena.getGlobalFreeFractionAfterEmptying(totalCapacity, totalUnfragmentedFree) < FREE_FRACTION_AFTER_DEALLOC_ABORT_LIMIT) {
                    emptyingArena.setEmptying(false);
                    emptyingArena = null;
                }
                // remove if emptying results in empty
                else if (emptyingArena.continueEmptying(commands, budget)) {
                    emptyingArena.deleteShared(commands);
                    this.arenas.remove(emptyingArena);
                    emptyingArena = null;
                }
            }

            // stop if the budget has been used up
            if (emptyingArena != null && budget.isElementBudgetEmpty()) {
                return;
            }

            // run defragmentation and find the least used arena that's not currently emptying to potentially empty
            SharedGlBufferArena leastUsedArena = null;
            for (int i = 0; i < this.arenas.size(); i++) {
                int arenaIndex = (ArenaAggregator.this.arenaDefragOffset + i) % this.arenas.size();
                var arena = this.arenas.get(arenaIndex);

                if (!arena.isEmptying()) {
                    if (leastUsedArena == null || arena.getUsed() < leastUsedArena.getUsed()) {
                        leastUsedArena = arena;
                    }

                    if (!budget.isElementBudgetEmpty()) {
                        arena.defragmentIncremental(commands, budget);
                    }
                }
            }

            // check if we can deallocate the least used arena by relocating its data into the others
            if (emptyingArena == null && leastUsedArena != null && this.arenas.size() > 1 &&
                    leastUsedArena.getGlobalFreeFractionAfterEmptying(totalCapacity, totalUnfragmentedFree) >= MIN_FREE_FRACTION_AFTER_DEALLOC) {
                leastUsedArena.setEmptying(true);
            }
        }
    }

    public ArenaAggregator(StagingBuffer stagingBuffer) {
        this.stagingBuffer = stagingBuffer;

        this.dataTypes = new ArrayList<>();
        this.dataTypes.add(this.index);
        this.dataTypes.add(this.geometry);

        addStrideIfNotExists(8);
        addStrideIfNotExists(16);
        addStrideIfNotExists(20);
        addStrideIfNotExists(24);
        addStrideIfNotExists(28);
        addStrideIfNotExists(32);
        addStrideIfNotExists(36);
        addStrideIfNotExists(40);
        addStrideIfNotExists(48);
    }

    private void addStrideIfNotExists(int stride) {
        boolean exists = false;
        for (DataType dt : this.dataTypes) {
            if (dt.stride == stride) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            long sharedSizeBytes = (long) (this.geometry.sharedSizeBytes *
                    ((double) stride / this.geometry.stride));
            this.dataTypes.add(new DataType("Stride-" + stride, stride, sharedSizeBytes));
        }
    }

    public RegionAllocatorHandle getGeometryBufferAllocator(CommandList commands, RenderRegion region, int stride, RegionAllocatorHandle.AllocationChangeConsumer onChange) {
        return createAllocator(commands, region, stride, onChange);
    }

    public RegionAllocatorHandle getIndexBufferAllocator(CommandList commands, RenderRegion region, int stride, RegionAllocatorHandle.AllocationChangeConsumer onChange) {
        return createAllocator(commands, region, stride, onChange);
    }

    private RegionAllocatorHandle createAllocator(CommandList commands, RenderRegion region, int stride, RegionAllocatorHandle.AllocationChangeConsumer onChange) {
        GlBufferArena backingArena = getArenaFittingFor(commands, 0, stride);
        return new RegionAllocatorHandle(region, onChange, backingArena);
    }

    private DataType getDataTypeForStride(int stride) {
        for (DataType dataType : this.dataTypes) {
            if (dataType.stride == stride) {
                return dataType;
            }
        }

        long sharedSizeBytes = calculateSharedSizeForStride(stride);
        DataType newDataType = new DataType("Dynamic-Stride-" + stride, stride, sharedSizeBytes);
        this.dataTypes.add(newDataType);

        return newDataType;
    }

    private long calculateSharedSizeForStride(int stride) {
        long baseVertices = this.geometry.sharedSize;
        return baseVertices * stride;
    }

    GlBufferArena getArenaFittingFor(CommandList commands, long requiredCapacity, int stride) {
        // TODO: create arena size based on top k region sizes, and scale up if all regions are big
        return getDataTypeForStride(stride).ensureSharedArena(commands, requiredCapacity);
    }

    GlBufferArena createDedicatedArena(CommandList commands, long requiredCapacity, int stride) {
        GlMutableBuffer buffer = getBufferOfSizeAtLeast(commands, requiredCapacity * stride);
        long actualCapacity = buffer.getSize() / stride;
        return new GlBufferArena(this, buffer, actualCapacity, stride);
    }

    GlMutableBuffer getBufferOfSizeAtLeast(CommandList commands, long bytes) {
        GlMutableBuffer buffer = null;

        if (freeBufferCount > 0) {
            // get any buffer of at least the requested size but at most MAX_BUFFER_REUSE_SIZE_FACTOR larger
            long maxAcceptableSize = (long) (bytes * MAX_BUFFER_REUSE_SIZE_FACTOR);

            // iterate buffers to get the smallest acceptable one
            int candidateIndex = -1;
            for (int i = 0; i < this.freeBuffers.length; i++) {
                GlMutableBuffer freeBuffer = this.freeBuffers[i];
                if (freeBuffer != null) {
                    long testSize = freeBuffer.getSize();
                    if (testSize >= bytes && testSize <= maxAcceptableSize &&
                            (buffer == null || testSize < buffer.getSize())) {
                        candidateIndex = i;
                        buffer = freeBuffer;
                    }
                }
            }
            if (buffer != null) {
                this.freeBuffers[candidateIndex] = null;
                freeBufferCount--;
            }
        }

        if (buffer == null) {
            buffer = commands.createMutableBuffer();
            commands.allocateStorage(buffer, bytes, BUFFER_USAGE);
        }
        return buffer;
    }

    void releaseBufferForReuse(CommandList commands, GlMutableBuffer buffer) {
        // find an empty slot if there is one
        if (freeBufferCount < this.freeBuffers.length) {
            for (int i = 0; i < this.freeBuffers.length; i++) {
                if (this.freeBuffers[i] == null) {
                    this.freeBuffers[i] = buffer;
                    freeBufferCount++;
                    return;
                }
            }
        }

        // evict randomly if no empty slot available
        int evictIndex = (int) (Math.random() * this.freeBuffers.length);
        commands.deleteBuffer(this.freeBuffers[evictIndex]);
        this.freeBuffers[evictIndex] = buffer;
    }

    public void delete(CommandList commands) {
        for (int i = 0; i < this.freeBuffers.length; i++) {
            GlMutableBuffer buffer = this.freeBuffers[i];
            if (buffer != null) {
                commands.deleteBuffer(buffer);
                this.freeBuffers[i] = null;
            }
        }
        freeBufferCount = 0;

        for (var dataType : this.dataTypes) {
            for (var arenaEntry : dataType.arenas) {
                arenaEntry.deleteShared(commands);
            }
            dataType.arenas.clear();
        }
    }

    public void update(CommandList commands) {
        // TODO: adjust based on total memory usage? if we have more memory usage we need to move more of it around
        var budget = new DefragBudget(DEFRAG_COPIES_PER_FRAME_BUDGET, DEFRAG_BYTES_PER_FRAME_BUDGET);

        // perform some amount of defragmentation on update
        var typeOffset = (int) Math.floor(Math.random() * this.dataTypes.size());
        for (int i = 0; i < this.dataTypes.size(); i++) {
            int dataTypeIndex = (typeOffset + i) % this.dataTypes.size();
            var dataType = this.dataTypes.get(dataTypeIndex);
            dataType.update(commands, budget);
        }

        this.totalCopyCount += budget.getUsedCopyCount();
        this.totalCopyBytes += budget.getUsedCopyBytes();
        this.arenaDefragOffset++;
    }

    public long getGeometryDeviceUsedMemory() {
        return this.geometry.getDeviceUsedMemory();
    }

    public long getIndexDeviceUsedMemory() {
        return this.index.getDeviceUsedMemory();
    }

    public long getGeometryDeviceAllocatedMemory() {
        return this.geometry.getDeviceAllocatedMemory();
    }

    public long getIndexDeviceAllocatedMemory() {
        return this.index.getDeviceAllocatedMemory();
    }

    public long getMiscAllocatedMemory() {
        long allocated = 0;
        for (GlMutableBuffer buffer : this.freeBuffers) {
            if (buffer != null) {
                allocated += buffer.getSize();
            }
        }
        return allocated;
    }

    public int getBufferCount() {
        int count = freeBufferCount;
        for (var dataType : this.dataTypes) {
            count += dataType.arenas.size();
        }
        return count;
    }

    public void renderBufferDebug(GuiGraphics graphics) {
        int leftPadding = 10;
        int verticalPadding = 10;
        int arenaPadding = 4;
        int targetWidth = graphics.guiWidth() / 2;
        int targetHeight = graphics.guiHeight();
        var totalMapHeight = (targetHeight - verticalPadding - 2 * verticalPadding * this.dataTypes.size());

        // count number of maps to adjust heights
        var countOffset = 2;
        int mapCount = this.dataTypes.stream().mapToInt(dt -> dt.arenas.size() + countOffset).sum();
        if (mapCount == 0) {
            return;
        }

        int y = verticalPadding;
        for (var dataType : this.dataTypes) {
            // dataType.name + " Shared Arenas: " + dataType.arenas.size()
            var str = String.format("%s Shared Arenas: %d x %d MiB (Used: %d MiB / Allocated: %d MiB)",
                    dataType.name,
                    dataType.arenas.size(),
                    MathUtil.toMib(dataType.sharedSizeBytes),
                    MathUtil.toMib(dataType.getDeviceUsedMemory()),
                    MathUtil.toMib(dataType.getDeviceAllocatedMemory()));
            graphics.drawString(Minecraft.getInstance().font, str, leftPadding, y, Colors.FOREGROUND);
            y += verticalPadding;
            var x = leftPadding;
            var arenaCount = dataType.arenas.size();
            if (arenaCount == 0) {
                continue;
            }

            int mapWidth = (targetWidth - leftPadding * 2 - arenaPadding * (arenaCount - 1)) / arenaCount;
            int mapHeight = totalMapHeight * (dataType.arenas.size() + countOffset) / mapCount;
            for (var arenaEntry : dataType.arenas) {
                arenaEntry.renderDebugMap(graphics, x, y, mapWidth, mapHeight);
                x += mapWidth + arenaPadding;
            }

            y += mapHeight + verticalPadding;
        }

        // show total copies and bytes
        graphics.drawString(Minecraft.getInstance().font,
                String.format("Defragmentation copies: %d (%d MiB)",
                        this.totalCopyCount, MathUtil.toMib(this.totalCopyBytes)),
                leftPadding, 30, Colors.FOREGROUND);

        // budget per frame
        graphics.drawString(Minecraft.getInstance().font,
                String.format("Defragmentation budget per frame: %d copies / %d MiB",
                        DEFRAG_COPIES_PER_FRAME_BUDGET,
                        MathUtil.toMib(DEFRAG_BYTES_PER_FRAME_BUDGET)),
                leftPadding, 40, Colors.FOREGROUND);
    }
}
