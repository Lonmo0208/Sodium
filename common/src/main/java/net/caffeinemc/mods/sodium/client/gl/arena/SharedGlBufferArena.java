package net.caffeinemc.mods.sodium.client.gl.arena;

import net.caffeinemc.mods.sodium.client.gl.buffer.GlMutableBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;

import java.util.ArrayList;
import java.util.List;

public class SharedGlBufferArena extends DefragmentingGlBufferArena implements SizedTreeMap.Sized {
    final SizedTreeMap<RegionAllocatorHandle> ownersByUsed = new SizedTreeMap<>();

    private final int identifier;
    private static int nextIdentifier = 1;

    protected SharedGlBufferArena(ArenaAggregator allocator, GlMutableBuffer initialBuffer, long capacity, int stride) {
        super(allocator, initialBuffer, capacity, stride);
        this.identifier = nextIdentifier++;
    }

    @Override
    public long getSize() {
        return this.getBiggestFreeSegmentSize();
    }

    @Override
    public long getIdentifier() {
        return this.identifier;
    }

    @Override
    public void deleteSingleOwner(CommandList commands) {
        // don't delete on single-owner deletion
    }

    public void deleteShared(CommandList commands) {
        super.deleteSingleOwner(commands);
    }

    @Override
    void updateUsed(long deltaUsed, RegionAllocatorHandle owner) {
        this.ownersByUsed.removeSized(owner);

        var segmentDelta = Long.signum(deltaUsed);
        this.used += deltaUsed;
        owner.used += deltaUsed;
        this.usedSegments += segmentDelta;
        owner.usedSegments += segmentDelta;

        this.ownersByUsed.addSized(owner);
    }

    @Override
    void handleResizeUploads(CommandList commands, RegionAllocatorHandle uploadingOwner, List<PendingUpload> queue, long totalOwnerUsageAfterUploads) {
        boolean relocatedUploadingOwner = false;

        // this needs to be a loop because the young gen buffer isn't guaranteed to be defragmented so we may need to evict more than one owner
        do {
            long biggestUsage = Long.MAX_VALUE; // max value to make sure the head map lookup works
            int biggestUsageSegmentCount = 0;
            RegionAllocatorHandle biggestUsageOwner = null;

            if (!relocatedUploadingOwner) {
                biggestUsage = totalOwnerUsageAfterUploads;
                biggestUsageSegmentCount = uploadingOwner.usedSegments + queue.size();
                biggestUsageOwner = uploadingOwner;
            }

            // check if there's another owner that is bigger than this owner will be when it is fully uploaded
            for (var entry : this.ownersByUsed.reversed().entrySet()) {
                var entryOwner = entry.getValue();
                if (entryOwner != uploadingOwner && entryOwner.used > biggestUsage) {
                    biggestUsage = entryOwner.used;
                    biggestUsageSegmentCount = entryOwner.usedSegments;
                    biggestUsageOwner = entryOwner;
                    break;
                }
            }

            if (biggestUsageOwner == uploadingOwner) {
                relocatedUploadingOwner = true;
            }

            // by construction, either the owner is the biggest one and is getting moved to its own arena, or another owner is bigger and this one will fit into this young gen arena

            // TODO: when estimating new capacity, take into account how full the section already is since a full section will not grow much anymore
            var newCapacity = GlBufferArena.estimateNewCapacity(biggestUsageSegmentCount, biggestUsageOwner.getFillFractionInv(), biggestUsage);
            transferToNewArena(commands, biggestUsageOwner, newCapacity);

            // try uploading again
            uploadingOwner.getBackingArena().tryUploads(commands, uploadingOwner, queue);
        } while (!queue.isEmpty());
    }

    private void transferToNewArena(CommandList commands, RegionAllocatorHandle owner, long newCapacity) {
        var targetArena = this.parent.getArenaFittingFor(commands, newCapacity, this.stride);
        if (targetArena == this) {
            throw new IllegalStateException("Target arena is the same as the source arena");
        }
        owner.setBackingArena(targetArena);
        this.ownersByUsed.removeSized(owner);

        // extract all segments owned by this owner, and reassign them to the new arena.
        // we also need to patch up the preceding and following segments to remove references to the extracted segments.
        this.checkAssertions();
        var extractedSegments = this.extractAllSegmentsOwnedBy(owner, targetArena);
        this.checkAssertions();

        // copy the extracted segments into the new arena
        targetArena.receiveSegmentsFrom(commands, extractedSegments, this.arenaBuffer, owner);

        // notify the owner that has been moved of the buffer change
        owner.notifyBufferChanged(commands);
    }

    @Override
    void receiveSegmentsFrom(CommandList commandList, List<GlBufferSegment> segments, GlMutableBuffer srcBufferObj, RegionAllocatorHandle owner) {
        this.used += owner.used;
        this.usedSegments += segments.size();
        if (this.used > this.capacity) {
            throw new UnsupportedOperationException("New capacity must be larger than used size");
        }

        this.ownersByUsed.addSized(owner);

        // find the target segment that's big enough to contain all segments
        var targetSegment = this.takeFree(owner.used);
        if (targetSegment == null) {
            throw new IllegalStateException("No free segment large enough to receive transferred segments even though there should be one");
        }
        long endOfFreePrefix = targetSegment.getEnd() - owner.used;
        var pendingCopies = this.buildTransferList(segments, endOfFreePrefix);

        this.executeCopyCommands(commandList, pendingCopies, srcBufferObj, this.arenaBuffer);

        this.finalizeInsertedSegments(targetSegment, endOfFreePrefix, segments);
    }

    private void finalizeInsertedSegments(GlBufferSegment targetSegment, long endOfFreePrefix, List<GlBufferSegment> segments) {
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("No segments to insert");
        }

        // new order: targetSegment.prev -> targetSegment (if any space left) -> segments... -> targetSegment.next
        // the target has not yet been resized at this point
        GlBufferSegment targetPrev = targetSegment.getPrev();
        GlBufferSegment targetNext = targetSegment.getNext();
        GlBufferSegment firstInserted = segments.getFirst();
        GlBufferSegment lastInserted = segments.getLast();

        // link lastInserted and targetNext
        lastInserted.setNext(targetNext);
        if (targetNext != null) {
            targetNext.setPrev(lastInserted);
        }

        // we need to resize the target segment
        if (endOfFreePrefix > targetSegment.getOffset()) {
            // there's space before the inserted segments, resize target to be that free space
            targetSegment.setLength(endOfFreePrefix - targetSegment.getOffset());

            // link targetSegment and firstInserted
            firstInserted.setPrev(targetSegment);
            targetSegment.setNext(firstInserted);
            // prev and target are already linked

            // target segment has already been removed from free list, add it back with new size
            this.addFreeSegment(targetSegment);
        } else {
            // no space before inserted segments, link targetPrev and firstInserted
            firstInserted.setPrev(targetPrev);
            if (targetPrev != null) {
                targetPrev.setNext(firstInserted);
            } else {
                // first inserted is now head
                this.head = firstInserted;
            }
        }

        this.checkAssertions();
    }

    private List<GlBufferSegment> extractAllSegmentsOwnedBy(RegionAllocatorHandle owner, AllocatorBase newAllocator) {
        ArrayList<GlBufferSegment> extractedSegments = new ArrayList<>();
        GlBufferSegment previousExtracted = null;
        GlBufferSegment current = this.head;
        this.checkAssertions();

        // extract segments owned by the specified owner, patching links of the segments that are not extracted, and correcting the links on the extracted segments to point to each other
        while (current != null) {
            GlBufferSegment next = current.getNext();

            if (current.getOwner() == owner) {
                // patch links, offsets, and lengths of surrounding segments
                extractSegment(current, next);

                extractedSegments.add(current);
                current.setAllocator(newAllocator);
                this.used -= current.getLength();

                this.checkAssertions();

                // link extracted segments together
                if (previousExtracted != null) {
                    previousExtracted.setNext(current);
                    current.setPrev(previousExtracted);
                } else {
                    current.setPrev(null);
                }

                previousExtracted = current;
            }
            current = next;
        }

        this.checkAssertions();

        return extractedSegments;
    }

    private void extractSegment(GlBufferSegment current, GlBufferSegment next) {
        GlBufferSegment prev = current.getPrev();

        // current is head
        if (current == this.head) {
            // next is null
            if (next == null) {
                // new free head
                this.head = GlBufferSegment.createFreeSegment(this, 0, current.getLength());
                this.addFreeSegment(this.head);
            }
            // next is free, expand it
            else if (next.isFree()) {
                this.head = next;
                next.setOffset(0);
                this.removeFreeSegment(next);
                next.setLength(next.getLength() + current.getLength());
                this.addFreeSegment(next);
            }
            // next is not free, create new free segment as head
            else {
                this.head = GlBufferSegment.createFreeSegment(this, 0, current.getLength());
                this.head.setNext(next);
                next.setPrev(this.head);
                this.addFreeSegment(this.head);
            }
        }
        // current is tail
        else if (next == null) {
            // current cannot be head since the other case handles that

            // prev is free, expand it
            if (prev.isFree()) {
                this.removeFreeSegment(prev);
                prev.setLength(prev.getLength() + current.getLength());
                prev.setNext(null);
                this.addFreeSegment(prev);
            }
            // prev is not free, create new free segment as tail
            else {
                GlBufferSegment newFreeTail = GlBufferSegment.createFreeSegment(this, current.getOffset(), current.getLength());
                prev.setNext(newFreeTail);
                newFreeTail.setPrev(prev);
                this.addFreeSegment(newFreeTail);
            }
        }
        // current is in the middle
        else {
            // both prev and next are free, expand prev
            if (prev.isFree() && next.isFree()) {
                this.removeFreeSegment(prev);
                this.removeFreeSegment(next);
                prev.setLength(prev.getLength() + current.getLength() + next.getLength());
                prev.setNext(next.getNext());
                if (next.getNext() != null) {
                    next.getNext().setPrev(prev);
                }
                this.addFreeSegment(prev);
            }
            // prev is free, expand it
            else if (prev.isFree()) {
                this.removeFreeSegment(prev);
                prev.setLength(prev.getLength() + current.getLength());
                prev.setNext(next);
                next.setPrev(prev);
                this.addFreeSegment(prev);
            }
            // next is free, expand it
            else if (next.isFree()) {
                this.removeFreeSegment(next);
                next.setOffset(current.getOffset());
                next.setLength(next.getLength() + current.getLength());
                next.setPrev(prev);
                prev.setNext(next);
                this.addFreeSegment(next);
            }
            // neither is free, create new free segment between them
            else {
                GlBufferSegment newFreeSegment = GlBufferSegment.createFreeSegment(this, current.getOffset(), current.getLength());
                prev.setNext(newFreeSegment);
                newFreeSegment.setPrev(prev);
                newFreeSegment.setNext(next);
                next.setPrev(newFreeSegment);
                this.addFreeSegment(newFreeSegment);
            }
        }
    }
}