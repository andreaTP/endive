package run.endive.redline.experimental.runner.jffi.internal;

import com.kenai.jffi.MemoryIO;
import run.endive.runtime.GlobalInstance;
import run.endive.wasm.types.MutabilityType;
import run.endive.wasm.types.ValType;
import run.endive.wasm.types.Value;

/**
 * GlobalInstance backed by an off-heap buffer via jffi MemoryIO.
 * Native code and Java code read/write the same memory — no sync needed.
 */
public final class JffiNativeGlobalInstance extends GlobalInstance {

    private static final MemoryIO MEM = MemoryIO.getInstance();

    // Not final: an imported global is built before the instance that will use it
    // exists, so it starts out on its own buffer and is moved onto the machine's
    // globals buffer by rebind() once there is one. See JffiNativeMachine.
    private long bufferAddress;
    private long offset;

    /**
     * True while this global sits on a buffer of its own rather than inside some
     * machine's globals buffer. Only such a global may be moved: one a module
     * exports is already where that module's compiled code reads it.
     */
    private boolean standalone;

    public JffiNativeGlobalInstance(
            long bufferAddress,
            int index,
            long initialValue,
            ValType valType,
            MutabilityType mutabilityType) {
        super(valType, mutabilityType, initialValue, 0);
        this.bufferAddress = bufferAddress;
        this.offset = (long) index * 8;
        // Write initial value to buffer
        MEM.putLong(bufferAddress + offset, initialValue);
    }

    /**
     * A global for the host to pass in as an import, on storage of its own until the
     * instance that receives it adopts it.
     */
    public static JffiNativeGlobalInstance standalone(
            long initialValue, ValType valType, MutabilityType mutabilityType) {
        var global =
                new JffiNativeGlobalInstance(
                        MEM.allocateMemory(8, true), 0, initialValue, valType, mutabilityType);
        global.standalone = true;
        return global;
    }

    boolean isStandalone() {
        return standalone;
    }

    /**
     * Moves this global onto the buffer compiled code reads, carrying its current
     * value across. Both sides then share the same eight bytes, so neither can go
     * stale while the other writes.
     */
    void rebind(long target, int index) {
        long current = getValue();
        this.bufferAddress = target;
        this.offset = (long) index * 8;
        this.standalone = false;
        MEM.putLong(target + offset, current);
    }

    @Override
    public long getValue() {
        return MEM.getLong(bufferAddress + offset);
    }

    @Override
    public long getValueLow() {
        return MEM.getLong(bufferAddress + offset);
    }

    @Override
    public void setValue(long value) {
        MEM.putLong(bufferAddress + offset, value);
    }

    @Override
    public void setValue(Value value) {
        checkType(value);
        MEM.putLong(bufferAddress + offset, value.raw());
    }

    @Override
    public void setValueLow(long value) {
        MEM.putLong(bufferAddress + offset, value);
    }
}
