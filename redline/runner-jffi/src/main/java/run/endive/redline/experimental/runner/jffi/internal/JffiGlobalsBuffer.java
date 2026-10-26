package run.endive.redline.experimental.runner.jffi.internal;

import com.kenai.jffi.MemoryIO;

/**
 * An instance's globals buffer, freed once nothing reaches it: neither its machine nor a
 * global bound to it. Separate from the machine, so a host global keeps only this alive.
 */
final class JffiGlobalsBuffer {

    private static final MemoryIO MEM = MemoryIO.getInstance();

    private final long address;

    JffiGlobalsBuffer(int globalCount) {
        long size = (long) globalCount * 8;
        long allocated = MEM.allocateMemory(size, true);
        if (allocated == 0) {
            throw new OutOfMemoryError("Failed to allocate " + size + " native bytes");
        }
        this.address = allocated;
        JffiCleaner.register(this, () -> MEM.freeMemory(allocated));
    }

    /** The buffer's address: keep this object reachable while using it. */
    long address() {
        return address;
    }
}
