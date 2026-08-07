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
 * Tests that a call to a function with >253 params inside a try_table
 * does not corrupt values below the try scope.
 * Regression test for a latent bug in computeMaxTempSlots (CALL missing).
 */
public class CallManyParamsTryTest {

    private static final WasmModule MODULE =
            Parser.parse(CorpusResources.getResource("compiled/call_many_params_try.wat.wasm"));

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
    public void callManyBelowTry(
            String name, Function<Instance.Builder, Instance.Builder> machineInject) {
        var instance = machineInject.apply(Instance.builder(MODULE)).build();
        assertEquals(42, instance.export("call-many-below-try").apply()[0]);
    }
}
