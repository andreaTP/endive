package run.endive.redline.experimental.runner.internal;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import run.endive.runtime.GlobalInstance;
import run.endive.wasm.types.MutabilityType;
import run.endive.wasm.types.ValType;
import run.endive.wasm.types.Value;

/**
 * GlobalInstance backed by an off-heap MemorySegment buffer.
 * Native code and Java code read/write the same memory — no sync needed.
 */
public final class NativeGlobalInstance extends GlobalInstance {

    // Not final: an imported global is built before the instance that will use it
    // exists, so it starts out on its own buffer and is moved onto the machine's
    // globals buffer by rebind() once there is one. See NativeMachine.
    private MemorySegment buffer;
    private long offset;

    /**
     * True while this global sits on a buffer of its own rather than inside some
     * machine's globals buffer. Only such a global may be moved: one a module
     * exports is already where that module's compiled code reads it.
     */
    private boolean standalone;

    public NativeGlobalInstance(
            MemorySegment buffer,
            int index,
            long initialValue,
            ValType valType,
            MutabilityType mutabilityType) {
        super(valType, mutabilityType, initialValue, 0);
        this.buffer = buffer;
        this.offset = (long) index * 8;
        // Write initial value to buffer
        buffer.set(ValueLayout.JAVA_LONG, offset, initialValue);
    }

    /**
     * A global for the host to pass in as an import, on storage of its own until the
     * instance that receives it adopts it.
     */
    public static NativeGlobalInstance standalone(
            long initialValue, ValType valType, MutabilityType mutabilityType) {
        var global =
                new NativeGlobalInstance(
                        Arena.ofAuto().allocate(8, 8), 0, initialValue, valType, mutabilityType);
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
    void rebind(MemorySegment target, int index) {
        long current = getValue();
        this.buffer = target;
        this.offset = (long) index * 8;
        this.standalone = false;
        target.set(ValueLayout.JAVA_LONG, offset, current);
    }

    @Override
    public long getValue() {
        return buffer.get(ValueLayout.JAVA_LONG, offset);
    }

    @Override
    public long getValueLow() {
        return buffer.get(ValueLayout.JAVA_LONG, offset);
    }

    @Override
    public void setValue(long value) {
        buffer.set(ValueLayout.JAVA_LONG, offset, value);
    }

    @Override
    public void setValue(Value value) {
        checkType(value);
        buffer.set(ValueLayout.JAVA_LONG, offset, value.raw());
    }

    @Override
    public void setValueLow(long value) {
        buffer.set(ValueLayout.JAVA_LONG, offset, value);
    }
}
