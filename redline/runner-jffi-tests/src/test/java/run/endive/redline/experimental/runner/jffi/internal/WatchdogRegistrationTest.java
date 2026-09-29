package run.endive.redline.experimental.runner.jffi.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import run.endive.corpus.CorpusResources;
import run.endive.redline.experimental.api.internal.InterruptWatchdog;
import run.endive.runtime.HostFunction;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.testing.NativeInstanceBuilder;
import run.endive.wasm.Parser;
import run.endive.wasm.types.FunctionType;

/** Calls are watched by the shared poller, one registration per outermost call. */
public class WatchdogRegistrationTest {

    // well below the reentrant stack guard, which fires near 115
    private static final int DEPTH = 20;

    private static final int ROUNDS = 20;

    @Test
    public void nestingRegistersOnlyTheOutermostCall() {
        int before = InterruptWatchdog.activeCount();
        int[] deepest = {0};
        withReentrantInstance(
                instance -> instance.export("recurse").apply(),
                () -> deepest[0] = Math.max(deepest[0], InterruptWatchdog.activeCount()));

        assertEquals(1, deepest[0] - before);
        assertEquals(before, InterruptWatchdog.activeCount());
    }

    @Test
    public void callingStartsNoThreads() {
        var threads = ManagementFactory.getThreadMXBean();
        long[] started = {0};

        withReentrantInstance(
                instance -> {
                    // starts the shared poller
                    instance.export("recurse").apply();

                    long before = threads.getTotalStartedThreadCount();
                    for (int i = 0; i < ROUNDS; i++) {
                        instance.export("recurse").apply();
                    }
                    started[0] = threads.getTotalStartedThreadCount() - before;
                },
                () -> {});

        // loose bound: JIT compiler threads may start meanwhile
        assertTrue(started[0] < ROUNDS, started[0] + " threads started");
    }

    private static void withReentrantInstance(Consumer<Instance> body, Runnable atEachLevel) {
        var module =
                Parser.parse(CorpusResources.getResource("compiled/reentrant-recursion.wat.wasm"));

        int[] depth = {0};
        var imports =
                ImportValues.builder()
                        .addFunction(
                                new HostFunction(
                                        "host",
                                        "reenter",
                                        FunctionType.of(List.of(), List.of()),
                                        (Instance inst, long... args) -> {
                                            atEachLevel.run();
                                            if (depth[0]++ < DEPTH) {
                                                inst.export("recurse").apply();
                                            }
                                            depth[0] = 0;
                                            return null;
                                        }))
                        .build();

        try (var instance =
                NativeInstanceBuilder.builder(module).withImportValues(imports).build()) {
            body.accept(instance);
        }
    }
}
