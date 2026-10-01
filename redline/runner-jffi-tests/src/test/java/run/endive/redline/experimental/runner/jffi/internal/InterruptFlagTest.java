package run.endive.redline.experimental.runner.jffi.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import run.endive.corpus.CorpusResources;
import run.endive.runtime.HostFunction;
import run.endive.runtime.ImportValues;
import run.endive.testing.NativeInstanceBuilder;
import run.endive.wasm.Parser;
import run.endive.wasm.types.FunctionType;

/**
 * The watchdog raises the interrupt flag from another thread, so it can land after
 * the call it was meant to stop has passed its last check. The flag must not then
 * sit in the context and stop a later call that nobody interrupted.
 */
public class InterruptFlagTest {

    @AfterEach
    public void clearInterruptStatus() {
        // Keeps a failure from leaking an interrupt into the rest of the suite.
        Thread.interrupted();
    }

    @Test
    public void aFlagRaisedMidCallDoesNotStopTheNextCall() {
        var module =
                Parser.parse(CorpusResources.getResource("compiled/interrupt-midcall.wat.wasm"));

        var machineRef = new JffiNativeMachine[1];
        var imports =
                ImportValues.builder()
                        .addFunction(
                                new HostFunction(
                                        "host",
                                        "raiseFlag",
                                        FunctionType.of(List.of(), List.of()),
                                        (inst, args) -> {
                                            machineRef[0].requestInterrupt();
                                            return null;
                                        }))
                        .build();

        try (var instance =
                NativeInstanceBuilder.builder(module).withImportValues(imports).build()) {
            machineRef[0] = (JffiNativeMachine) instance.getMachine();

            // Returns normally: the entry check ran before the flag was raised.
            instance.export("callHost").apply();

            assertEquals(
                    42,
                    (int) instance.export("answer").apply()[0],
                    "a flag left over from the previous call must not stop this one");
            assertFalse(
                    Thread.currentThread().isInterrupted(),
                    "no interrupt happened, so the caller must not be left interrupted");
        }
    }

    @Test
    public void aFlagRaisedInANestedCallDoesNotStopTheOuterCall() {
        var module =
                Parser.parse(CorpusResources.getResource("compiled/reentrant-interrupt.wat.wasm"));

        var machineRef = new JffiNativeMachine[1];
        var imports =
                ImportValues.builder()
                        .addFunction(
                                new HostFunction(
                                        "host",
                                        "reenter",
                                        FunctionType.of(List.of(), List.of()),
                                        (inst, args) -> {
                                            inst.export("raise").apply();
                                            return null;
                                        }))
                        .addFunction(
                                new HostFunction(
                                        "host",
                                        "raiseFlag",
                                        FunctionType.of(List.of(), List.of()),
                                        (inst, args) -> {
                                            machineRef[0].requestInterrupt();
                                            return null;
                                        }))
                        .addFunction(
                                new HostFunction(
                                        "host",
                                        "tick",
                                        FunctionType.of(List.of(), List.of()),
                                        (inst, args) -> null))
                        .build();

        try (var instance =
                NativeInstanceBuilder.builder(module).withImportValues(imports).build()) {
            machineRef[0] = (JffiNativeMachine) instance.getMachine();

            assertEquals(
                    1000,
                    (int) instance.export("run").apply()[0],
                    "a flag left over from the nested call must not stop the outer one");
        }
    }
}
