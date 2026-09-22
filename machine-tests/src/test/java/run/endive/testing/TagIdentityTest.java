package run.endive.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import run.endive.compiler.MachineFactoryCompiler;
import run.endive.corpus.CorpusResources;
import run.endive.runtime.ImportTag;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.runtime.InterpreterMachine;
import run.endive.runtime.TagInstance;
import run.endive.runtime.WasmException;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmModule;
import run.endive.wasm.types.TagType;

/** Tags match by identity, so two imported tags sharing a signature stay distinct. */
public class TagIdentityTest {

    private static final WasmModule MODULE =
            Parser.parse(CorpusResources.getResource("compiled/tag_identity.wat.wasm"));

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
        // both tags have the type (param i32), the only function type in the module
        var type = MODULE.typeSection().getType(0);
        var imports =
                ImportValues.builder()
                        .addTag(
                                new ImportTag(
                                        "host",
                                        "a",
                                        new TagInstance(new TagType((byte) 0, 0), type)))
                        .addTag(
                                new ImportTag(
                                        "host",
                                        "b",
                                        new TagInstance(new TagType((byte) 0, 0), type)))
                        .build();
        return machineInject.apply(Instance.builder(MODULE).withImportValues(imports)).build();
    }

    @ParameterizedTest
    @MethodSource("machineImplementations")
    public void sameTagCatches(Function<Instance.Builder, Instance.Builder> machineInject) {
        assertEquals(7, instance(machineInject).export("a-catches-a").apply()[0]);
    }

    @ParameterizedTest
    @MethodSource("machineImplementations")
    public void otherTagWithSameSignatureDoesNotCatch(
            Function<Instance.Builder, Instance.Builder> machineInject) {
        var instance = instance(machineInject);
        var e = assertThrows(WasmException.class, () -> instance.export("b-catches-a").apply());
        assertSame(instance.tag(0), e.instance().tag(e.tagIdx()));
        assertEquals(7, e.args()[0]);
    }
}
