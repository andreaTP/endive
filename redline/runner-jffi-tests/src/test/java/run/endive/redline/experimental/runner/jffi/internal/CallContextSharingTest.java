package run.endive.redline.experimental.runner.jffi.internal;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import run.endive.corpus.CorpusResources;
import run.endive.redline.experimental.api.NativeCode;
import run.endive.redline.experimental.api.internal.RedlineTarget;
import run.endive.redline.experimental.compiler.internal.NativeCompiler;
import run.endive.redline.experimental.runner.jffi.JffiNativeMachineFactory;
import run.endive.runtime.Instance;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmModule;

/** jffi never frees a call context, so instances must share theirs instead of making new ones. */
public class CallContextSharingTest {

    @Test
    public void instancesOfOneModuleShareTheirCallContexts() {
        var module = Parser.parse(CorpusResources.getResource("compiled/functions_10.wat.wasm"));
        var code =
                NativeCompiler.compile(RedlineTarget.detectHost().orElseThrow().triple(), module);

        try (var first = build(module, code);
                var second = build(module, code)) {
            var a = (JffiNativeMachine) first.getMachine();
            var b = (JffiNativeMachine) second.getMachine();
            int compared = 0;
            for (int funcId = 0; funcId < a.functionCount(); funcId++) {
                if (a.entryCallContext(funcId) != null) {
                    assertSame(a.entryCallContext(funcId), b.entryCallContext(funcId));
                    compared++;
                }
            }
            assertTrue(compared > 0, "no compiled function to compare");
        }
    }

    private static Instance build(WasmModule module, NativeCode code) {
        return JffiNativeMachineFactory.builder(module).withPrecompiledCode(code).build();
    }
}
