package run.endive.testing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import run.endive.compiler.MachineFactoryCompiler;
import run.endive.corpus.CorpusResources;
import run.endive.runtime.HostFunction;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.runtime.InterpreterMachine;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmModule;
import run.endive.wasm.types.ExternalType;
import run.endive.wasm.types.FunctionImport;
import run.endive.wasm.types.FunctionType;

/**
 * Reproducer for https://github.com/bytecodealliance/endive/issues/139
 *
 * A Kotlin/Wasm module traps on the compiler with "null array reference"
 * at array.len in kotlin.String.foldChars, while the interpreter runs it fine.
 */
public class KotlinFoldCharsTest {

    private static final WasmModule MODULE =
            Parser.parse(CorpusResources.getResource("compiled/trap.kt.wasm"));

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

    private static ImportValues buildImports(WasmModule module) {
        ImportValues.Builder imports = ImportValues.builder();
        for (int i = 0; i < module.importSection().importCount(); i++) {
            if (module.importSection().getImport(i).importType() != ExternalType.FUNCTION) {
                continue;
            }
            FunctionImport imported = (FunctionImport) module.importSection().getImport(i);
            FunctionType type = module.typeSection().getType(imported.typeIndex());
            imports.addFunction(
                    new HostFunction(
                            imported.module(),
                            imported.name(),
                            type,
                            (instance, callArgs) -> {
                                if (imported.name().equals("call_host")) {
                                    byte[] reply = "err\nNo such method nope".getBytes(UTF_8);
                                    instance.memory().write((int) callArgs[2], reply);
                                    return new long[] {reply.length};
                                }
                                return new long[type.returns().size()];
                            }));
        }
        return imports.build();
    }

    // Issue #139: interpreter succeeds, compiler traps with "null array reference"
    @ParameterizedTest(name = "{0}")
    @MethodSource("machineImplementations")
    public void kotlinWasmStartDoesNotTrap(
            String name, Function<Instance.Builder, Instance.Builder> machineInject) {
        var instance =
                machineInject
                        .apply(
                                Instance.builder(MODULE)
                                        .withImportValues(buildImports(MODULE))
                                        .withStart(false))
                        .build();

        assertDoesNotThrow(() -> instance.export("_start").apply());
    }
}
