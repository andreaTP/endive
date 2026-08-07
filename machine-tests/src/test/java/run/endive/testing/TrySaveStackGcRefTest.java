package run.endive.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import run.endive.compiler.MachineFactoryCompiler;
import run.endive.corpus.CorpusResources;
import run.endive.runtime.Instance;
import run.endive.runtime.InterpreterMachine;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmModule;

/**
 * Tests that GC ref values below a try_table scope are preserved.
 * Regression test for https://github.com/bytecodealliance/endive/issues/139
 */
public class TrySaveStackGcRefTest {

    private static final WasmModule MODULE =
            Parser.parse(CorpusResources.getResource("compiled/try_save_stack_gcref.wat.wasm"));

    private static Stream<Arguments> machineImplementations() {
        return Stream.of(
                Arguments.of(
                        "interpreter",
                        (Function<Instance.Builder, Instance.Builder>)
                                (b) -> b.withMachineFactory(InterpreterMachine::new)),
                Arguments.of(
                        "compiler",
                        (Function<Instance.Builder, Instance.Builder>)
                                (b) -> b.withMachineFactory(MachineFactoryCompiler::compile)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("machineImplementations")
    public void gcrefBelowTryCatch(
            String name, Function<Instance.Builder, Instance.Builder> machineInject) {
        var instance = machineInject.apply(Instance.builder(MODULE)).build();
        assertEquals(42, instance.export("gcref-below-try-catch").apply()[0]);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("machineImplementations")
    public void gcrefBelowTryNormal(
            String name, Function<Instance.Builder, Instance.Builder> machineInject) {
        var instance = machineInject.apply(Instance.builder(MODULE)).build();
        assertEquals(77, instance.export("gcref-below-try-normal").apply()[0]);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("machineImplementations")
    public void gcrefBelowNestedTry(
            String name, Function<Instance.Builder, Instance.Builder> machineInject) {
        var instance = machineInject.apply(Instance.builder(MODULE)).build();
        assertEquals(33, instance.export("gcref-below-nested-try").apply()[0]);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("machineImplementations")
    public void mixedStackBelowTry(
            String name, Function<Instance.Builder, Instance.Builder> machineInject) {
        var instance = machineInject.apply(Instance.builder(MODULE)).build();
        assertEquals(150, instance.export("mixed-stack-below-try").apply()[0]);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("machineImplementations")
    public void gcrefBelowDeepTryBr(
            String name, Function<Instance.Builder, Instance.Builder> machineInject) {
        var instance = machineInject.apply(Instance.builder(MODULE)).build();
        assertEquals(42, instance.export("gcref-below-deep-try-br").apply()[0]);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("machineImplementations")
    public void catchGcRefTagParam(
            String name, Function<Instance.Builder, Instance.Builder> machineInject) {
        var instance = machineInject.apply(Instance.builder(MODULE)).build();
        assertEquals(177, instance.export("catch-gcref-tag-param").apply()[0]);
    }
}
