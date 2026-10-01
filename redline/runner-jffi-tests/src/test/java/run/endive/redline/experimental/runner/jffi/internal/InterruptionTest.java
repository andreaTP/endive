package run.endive.redline.experimental.runner.jffi.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import run.endive.corpus.CorpusResources;
import run.endive.runtime.HostFunction;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.testing.NativeInstanceBuilder;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmEngineException;
import run.endive.wasm.types.FunctionType;

public class InterruptionTest {

    @Test
    public void shouldInterruptLoopViaThread() throws InterruptedException {
        var instance =
                buildInstance("compiled/infinite-loop.c.wasm", ImportValues.builder().build());
        var function = instance.export("run");
        assertThreadInterruption(function::apply, null, instance);
    }

    @Test
    public void shouldInterruptCallViaThread() throws InterruptedException {
        var instance = buildInstance("compiled/power.c.wasm", ImportValues.builder().build());
        var function = instance.export("run");
        assertThreadInterruption(() -> function.apply(100), null, instance);
    }

    @Test
    public void shouldInterruptNestedLoopViaThread() throws InterruptedException {
        var running = new CountDownLatch(1);
        var instance =
                buildInstance(
                        "compiled/reentrant-interrupt.wat.wasm",
                        reentrantImports(inst -> inst.export("spin").apply(), running));
        var function = instance.export("run");
        assertThreadInterruption(function::apply, running, instance);
    }

    @Test
    public void shouldInterruptLoopInAnotherMachineViaThread() throws InterruptedException {
        var running = new CountDownLatch(1);
        var inner =
                buildInstance(
                        "compiled/reentrant-interrupt.wat.wasm",
                        reentrantImports(inst -> {}, running));
        var outer =
                buildInstance(
                        "compiled/reentrant-interrupt.wat.wasm",
                        reentrantImports(inst -> inner.export("spin").apply(), running));
        var function = outer.export("run");
        assertThreadInterruption(function::apply, running, outer, inner);
    }

    // interrupts once `running` is released, or after 100ms without one
    private static void assertThreadInterruption(
            Runnable function, CountDownLatch running, Instance... instances)
            throws InterruptedException {
        AtomicBoolean interrupted = new AtomicBoolean();
        var failure = new AtomicReference<Throwable>();
        Thread thread =
                new Thread(
                        () -> {
                            var e = assertThrows(WasmEngineException.class, function::run);
                            assertEquals("interrupted", e.getMessage());
                            interrupted.set(true);
                        });
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(
                (t, e) -> {
                    failure.set(e);
                    if (running != null) {
                        running.countDown();
                    }
                });
        thread.start();
        boolean started = true;
        if (running == null) {
            Thread.sleep(100);
        } else {
            started = running.await(10, SECONDS);
        }

        thread.interrupt();
        SECONDS.timedJoin(thread, 10);
        if (failure.get() != null) {
            fail("the call failed", failure.get());
        }
        assertTrue(started, "the call never reached its loop");
        // a call still running uses the instances' memory, so they are leaked, not closed
        assertFalse(thread.isAlive(), "the call was not interrupted");
        for (var instance : instances) {
            instance.close();
        }
        assertTrue(interrupted.get());
    }

    private static ImportValues reentrantImports(
            Consumer<Instance> reenter, CountDownLatch running) {
        var noParams = FunctionType.of(List.of(), List.of());
        return ImportValues.builder()
                .addFunction(
                        new HostFunction(
                                "host",
                                "reenter",
                                noParams,
                                (inst, args) -> {
                                    reenter.accept(inst);
                                    return null;
                                }))
                .addFunction(new HostFunction("host", "raiseFlag", noParams, (inst, args) -> null))
                .addFunction(
                        new HostFunction(
                                "host",
                                "tick",
                                noParams,
                                (inst, args) -> {
                                    running.countDown();
                                    return null;
                                }))
                .build();
    }

    private static Instance buildInstance(String resource, ImportValues imports) {
        var module = Parser.parse(CorpusResources.getResource(resource));
        return NativeInstanceBuilder.builder(module).withImportValues(imports).build();
    }
}
