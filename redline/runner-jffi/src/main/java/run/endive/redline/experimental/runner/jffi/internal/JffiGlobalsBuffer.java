package run.endive.redline.experimental.runner.jffi.internal;

import com.kenai.jffi.MemoryIO;
import run.endive.redline.experimental.api.internal.NativeFootprint;

/** An instance's globals buffer, apart from its machine so a bound global keeps only this. */
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
        JffiCleaner.register(
                this, () -> MEM.freeMemory(allocated), () -> NativeFootprint.freed(size));
        NativeFootprint.allocated(size);
    }

    /** The buffer's address: keep this object reachable while using it. */
    long address() {
        return address;
    }
}
