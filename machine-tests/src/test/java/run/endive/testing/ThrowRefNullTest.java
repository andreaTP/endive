package run.endive.testing;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import run.endive.compiler.MachineFactoryCompiler;
import run.endive.corpus.CorpusResources;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.runtime.InterpreterMachine;
import run.endive.runtime.TrapException;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmModule;

/** Tests that `throw_ref` on a null exception reference traps. */
public class ThrowRefNullTest {

    private static final WasmModule MODULE =
            Parser.parse(CorpusResources.getResource("compiled/throw_ref_null.wat.wasm"));

    private static Stream<Arguments> machineImplementations() {
        return Stream.of(
                Arguments.of(
                        (Function<Instance.Builder, Instance.Builder>)
                                (b) -> b.withMachineFactory(InterpreterMachine::new)),
                Arguments.of(
                        (Function<Instance.Builder, Instance.Builder>)
                                (b) -> b.withMachineFactory(MachineFactoryCompiler::compile)));
    }

    private static Instance instance(Function<Instance.Builder, Instance.Builder> machineInject) {
        return machineInject
                .apply(Instance.builder(MODULE).withImportValues(ImportValues.builder().build()))
                .build();
    }

    @ParameterizedTest
    @MethodSource("machineImplementations")
    public void throwNullTraps(Function<Instance.Builder, Instance.Builder> machineInject) {
        var instance = instance(machineInject);
        assertThrows(TrapException.class, () -> instance.export("throw-null").apply());
    }

    /** A trap is not an exception, so try_table must not catch it. */
    @ParameterizedTest
    @MethodSource("machineImplementations")
    public void throwNullIsNotCatchable(
            Function<Instance.Builder, Instance.Builder> machineInject) {
        var instance = instance(machineInject);
        assertThrows(TrapException.class, () -> instance.export("throw-null-in-try").apply());
    }
}
