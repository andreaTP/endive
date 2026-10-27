package run.endive.redline.experimental.runner.jffi.internal;

import com.kenai.jffi.MemoryIO;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import run.endive.runtime.GlobalInstance;
import run.endive.runtime.Instance;
import run.endive.wasm.types.MutabilityType;
import run.endive.wasm.types.ValType;
import run.endive.wasm.types.Value;

/**
 * A global in an instance's off-heap globals buffer once bound, and on the heap before. The
 * buffer stays allocated for as long as a global bound to it is reachable.
 */
public final class JffiNativeGlobalInstance extends GlobalInstance {

    private static final MemoryIO MEM = MemoryIO.getInstance();

    /** Created by the host to be imported, so an instance may adopt it. */
    private final boolean hostCreated;

    // null while the value is on the heap; published whole, so a reader never pairs one
    // buffer with another's address
    private volatile Slot slot;
    // the machine whose buffer holds the value, weak so a host global doesn't keep it alive
    private volatile WeakReference<JffiNativeMachine> boundTo;

    private static final class Slot {
        final JffiGlobalsBuffer buffer;
        final long address;

        Slot(JffiGlobalsBuffer buffer, long address) {
            this.buffer = buffer;
            this.address = address;
        }
    }

    private JffiNativeGlobalInstance(
            long initialValue,
            ValType valType,
            MutabilityType mutabilityType,
            boolean hostCreated) {
        super(valType, mutabilityType, initialValue, 0);
        this.hostCreated = hostCreated;
    }

    /** A global a module defines, bound to its instance's buffer by {@link #setInstance}. */
    public static JffiNativeGlobalInstance defined(
            long initialValue, ValType valType, MutabilityType mutabilityType) {
        return new JffiNativeGlobalInstance(initialValue, valType, mutabilityType, false);
    }

    /** A global for the host to pass in as an import. */
    public static JffiNativeGlobalInstance hostCreated(
            long initialValue, ValType valType, MutabilityType mutabilityType) {
        return new JffiNativeGlobalInstance(initialValue, valType, mutabilityType, true);
    }

    /**
     * Moves a host global onto slot {@code index} of {@code owner}'s buffer, unless an open
     * instance is using it. Checked and moved under one lock, so two instances can't both take it.
     *
     * @return whether it moved
     */
    synchronized boolean adopt(JffiGlobalsBuffer buffer, int index, JffiNativeMachine owner) {
        var machine = boundTo == null ? null : boundTo.get();
        if (!hostCreated || (machine != null && !machine.isClosed())) {
            return false;
        }
        bind(buffer, index, owner);
        return true;
    }

    @Override
    public void setInstance(Instance instance) {
        super.setInstance(instance);
        if (instance.getMachine() instanceof JffiNativeMachine) {
            ((JffiNativeMachine) instance.getMachine()).bindDefinedGlobal(this);
        }
    }

    /** Moves the value onto slot {@code index} of {@code owner}'s {@code buffer}. */
    synchronized void bind(JffiGlobalsBuffer buffer, int index, JffiNativeMachine owner) {
        long address = buffer.address() + (long) index * 8;
        try {
            MEM.putLong(address, getValue());
        } finally {
            Reference.reachabilityFence(buffer);
        }
        this.boundTo = new WeakReference<>(owner);
        this.slot = new Slot(buffer, address);
    }

    @Override
    public long getValue() {
        var s = slot;
        if (s == null) {
            return super.getValueLow();
        }
        try {
            return MEM.getLong(s.address);
        } finally {
            Reference.reachabilityFence(s.buffer);
        }
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
        if (s == null) {
            super.setValueLow(value);
            return;
        }
        try {
            MEM.putLong(s.address, value);
        } finally {
            Reference.reachabilityFence(s.buffer);
        }
    }
}
