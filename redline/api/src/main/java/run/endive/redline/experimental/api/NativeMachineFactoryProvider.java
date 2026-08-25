package run.endive.redline.experimental.api;

import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import run.endive.runtime.Instance;
import run.endive.runtime.Memory;
import run.endive.runtime.TableInstance;
import run.endive.wasm.WasmModule;
import run.endive.wasm.types.MemoryLimits;
import run.endive.wasm.types.Table;

public interface NativeMachineFactoryProvider {

    Instance.Builder builder(WasmModule module, byte[][] precompiledCode);

    Memory createMemory(MemoryLimits limits);

    TableInstance createImportTable(Table table, int initValue);

    int priority();

    static Optional<NativeMachineFactoryProvider> discover() {
        NativeMachineFactoryProvider best = null;
        var it = ServiceLoader.load(NativeMachineFactoryProvider.class).iterator();
        while (it.hasNext()) {
            NativeMachineFactoryProvider provider;
            try {
                provider = it.next();
            } catch (ServiceConfigurationError | LinkageError e) {
                // This provider cannot be loaded on this JDK — the Panama runner is
                // compiled for 25, so instantiating it on an older JDK fails here.
                // Skip it and let a lower-priority provider win.
                //
                // The catch must wrap next(): ServiceLoader reports these failures
                // from the iterator, not from anything we do with the provider, so a
                // for-each loop would let them escape. next() has already advanced
                // past the failed provider, so this cannot spin.
                continue;
            }
            if (best == null || provider.priority() > best.priority()) {
                best = provider;
            }
        }
        return Optional.ofNullable(best);
    }
}
