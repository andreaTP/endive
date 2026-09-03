package run.endive.compiler;

import run.endive.wasm.WasmModule;

/**
 * A {@link MethodPrefixer} that takes each prefix from the module's name custom section, falling
 * back to {@link MethodPrefixer#DEFAULT_PREFIX} for functions the section does not name.
 */
public final class NameSectionMethodPrefixer implements MethodPrefixer {

    @Override
    public String getMethodPrefix(int funcId, WasmModule module) {
        var nameSection = module.nameSection();
        return nameSection == null ? null : nameSection.nameOfFunction(funcId);
    }
}
