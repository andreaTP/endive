package run.endive.redline.experimental.runner.internal;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.WeakReference;
import run.endive.runtime.GlobalInstance;
import run.endive.runtime.Instance;
import run.endive.wasm.types.MutabilityType;
import run.endive.wasm.types.ValType;
import run.endive.wasm.types.Value;

/** A global on the heap until bound to a globals buffer, which it then keeps alive. */
public final class NativeGlobalInstance extends GlobalInstance {

    /** Created by the host to be imported, so an instance may adopt it. */
    private final boolean hostCreated;

    // null while on the heap; published whole, so buffer and offset always match
    private record Slot(MemorySegment buffer, long offset) {}

    private volatile Slot slot;
    // the machine whose buffer holds the value, weak so a host global doesn't keep it alive
    private volatile WeakReference<NativeMachine> boundTo;

    private NativeGlobalInstance(
            long initialValue,
            ValType valType,
            MutabilityType mutabilityType,
            boolean hostCreated) {
        super(valType, mutabilityType, initialValue, 0);
        this.hostCreated = hostCreated;
        this.slot = new Slot(heapCell(initialValue), 0);
    }

    private static MemorySegment heapCell(long value) {
        return MemorySegment.ofArray(new long[] {value});
    }

    /** A global a module defines, bound to its instance's buffer by {@link #setInstance}. */
    public static NativeGlobalInstance defined(
            long initialValue, ValType valType, MutabilityType mutabilityType) {
        return new NativeGlobalInstance(initialValue, valType, mutabilityType, false);
    }

    /** A global for the host to pass in as an import. */
    public static NativeGlobalInstance hostCreated(
            long initialValue, ValType valType, MutabilityType mutabilityType) {
        return new NativeGlobalInstance(initialValue, valType, mutabilityType, true);
    }

    /**
     * Moves a host global onto slot {@code index} of {@code owner}'s buffer, unless an open
     * instance is using it.
     *
     * @return whether it moved
     */
    synchronized boolean adopt(MemorySegment target, int index, NativeMachine owner) {
        var machine = boundTo == null ? null : boundTo.get();
        if (!hostCreated || (machine != null && !machine.isClosed())) {
            return false;
        }
        bind(target, index, owner);
        return true;
    }

    @Override
    public void setInstance(Instance instance) {
        super.setInstance(instance);
        if (instance.getMachine() instanceof NativeMachine machine) {
            machine.bindDefinedGlobal(this);
        }
    }

    /** Moves the value onto slot {@code index} of {@code owner}'s {@code target}. */
    synchronized void bind(MemorySegment target, int index, NativeMachine owner) {
        long offset = (long) index * 8;
        target.set(ValueLayout.JAVA_LONG, offset, getValue());
        this.boundTo = new WeakReference<>(owner);
        this.slot = new Slot(target, offset);
    }

    @Override
    public long getValue() {
        var s = slot;
        return s.buffer().get(ValueLayout.JAVA_LONG, s.offset());
    }

    @Override
    public long getValueLow() {
        return getValue();
    }

    @Override
    public void setValue(long value) {
        setValueLow(value);
    }

    @Override
    public void setValue(Value value) {
        checkType(value);
        setValueLow(value.raw());
    }

    @Override
    public synchronized void setValueLow(long value) {
        var s = slot;
        s.buffer().set(ValueLayout.JAVA_LONG, s.offset(), value);
    }
}
