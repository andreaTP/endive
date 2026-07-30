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
        var loader = ServiceLoader.load(NativeMachineFactoryProvider.class);
        for (var provider : loader) {
            try {
                if (best == null || provider.priority() > best.priority()) {
                    best = provider;
                }
            } catch (ServiceConfigurationError e) {
                // Provider can't load on this JDK (e.g., Panama on JDK < 25) — skip
            }
        }
        return Optional.ofNullable(best);
    }
}
