package run.endive.redline.experimental.runner.jffi.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import run.endive.corpus.CorpusResources;
import run.endive.redline.experimental.api.internal.RedlineTarget;
import run.endive.redline.experimental.compiler.internal.NativeCompiler;
import run.endive.redline.experimental.runner.jffi.JffiNativeMachineFactory;
import run.endive.runtime.HostFunction;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmEngineException;
import run.endive.wasm.types.FunctionType;

public class InterruptionTest {

    @Test
    public void shouldInterruptLoopViaThread() throws InterruptedException {
        try (var instance = buildInstance("compiled/infinite-loop.c.wasm")) {
            var function = instance.export("run");
            assertThreadInterruption(function::apply);
        }
    }

    @Test
    public void shouldInterruptCallViaThread() throws InterruptedException {
        try (var instance = buildInstance("compiled/power.c.wasm")) {
            var function = instance.export("run");
            assertThreadInterruption(() -> function.apply(100));
        }
    }

    @Test
    public void shouldInterruptNestedLoopViaThread() throws InterruptedException {
        var imports =
                ImportValues.builder()
                        .addFunction(
                                new HostFunction(
                                        "host",
                                        "reenter",
                                        FunctionType.of(List.of(), List.of()),
                                        (inst, args) -> {
                                            inst.export("spin").apply();
                                            return null;
                                        }))
                        .addFunction(
                                new HostFunction(
                                        "host",
                                        "raiseFlag",
                                        FunctionType.of(List.of(), List.of()),
                                        (inst, args) -> null))
                        .build();
        try (var instance = buildInstance("compiled/reentrant-interrupt.wat.wasm", imports)) {
            var function = instance.export("run");
            assertThreadInterruption(function::apply);
        }
    }

    private static void assertThreadInterruption(Runnable function) throws InterruptedException {
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread thread =
                new Thread(
                        () -> {
                            var e = assertThrows(WasmEngineException.class, function::run);
                            assertEquals("interrupted", e.getMessage());
                            interrupted.set(true);
                        });
        thread.setDaemon(true);
        thread.start();
        Thread.sleep(100);

        thread.interrupt();
        SECONDS.timedJoin(thread, 10);
        assertTrue(interrupted.get());
    }

    private static Instance buildInstance(String resource) {
        return buildInstance(resource, ImportValues.builder().build());
    }

    private static Instance buildInstance(String resource, ImportValues imports) {
        var module = Parser.parse(CorpusResources.getResource(resource));
        return JffiNativeMachineFactory.builder(module)
                .withImportValues(imports)
                .withCompilerFunction(
                        m ->
                                NativeCompiler.compileAll(
                                        RedlineTarget.detectHost().orElseThrow().triple(), m))
                .build();
    }
}
