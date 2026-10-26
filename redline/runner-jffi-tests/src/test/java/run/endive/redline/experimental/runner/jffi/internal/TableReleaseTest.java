package run.endive.redline.experimental.runner.jffi.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import org.junit.jupiter.api.Test;
import run.endive.corpus.CorpusResources;
import run.endive.redline.experimental.api.internal.RedlineTarget;
import run.endive.redline.experimental.compiler.internal.NativeCompiler;
import run.endive.redline.experimental.runner.jffi.JffiNativeMachineFactory;
import run.endive.runtime.ImportTable;
import run.endive.runtime.ImportValues;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmModule;
import run.endive.wasm.types.Table;
import run.endive.wasm.types.TableLimits;
import run.endive.wasm.types.ValType;
import run.endive.wasm.types.Value;

/**
 * A table's buffer is off-heap and freed once nothing reaches the table, whether the module
 * declared it or the host created it to import: an instance never frees a table someone else
 * still holds.
 */
public class TableReleaseTest {

    @Test
    public void aTableTheModuleDeclaresIsReleasedWithItsInstance() throws InterruptedException {
        var table = declaredTableOfADroppedInstance();
        long deadline = System.nanoTime() + SECONDS.toNanos(30);
        while (table.get() != null) {
            assertTrue(System.nanoTime() < deadline, "the table is still reachable");
            System.gc();
            Thread.sleep(10);
        }
    }

    private static WeakReference<JffiNativeTable> declaredTableOfADroppedInstance() {
        var module = Parser.parse(CorpusResources.getResource("compiled/big-table.wat.wasm"));
        // no call first: the table belongs to the instance whether or not it ever ran
        var instance = build(module, null);
        return new WeakReference<>((JffiNativeTable) instance.table(0));
    }

    @Test
    public void closingKeepsATableTheModuleImported() {
        var module = Parser.parse(CorpusResources.getResource("compiled/imported-table.wat.wasm"));

        var borrowed =
                (JffiNativeTable)
                        JffiNativeMachineFactory.createImportTable(
                                new Table(ValType.FuncRef, new TableLimits(4, 4)),
                                Value.REF_NULL_VALUE);
        var imports =
                ImportValues.builder().addTable(new ImportTable("env", "table", borrowed)).build();

        try (var instance = build(module, imports)) {
            instance.export("noop").apply();
        }

        assertEquals(4, borrowed.size(), "an imported table belongs to whoever holds it");
        assertEquals(Value.REF_NULL_VALUE, borrowed.ref(0));
    }

    private static run.endive.runtime.Instance build(WasmModule module, ImportValues imports) {
        var builder =
                JffiNativeMachineFactory.builder(module)
                        .withPrecompiledCode(
                                NativeCompiler.compile(
                                        RedlineTarget.detectHost().orElseThrow().triple(), module));
        if (imports != null) {
            builder.withImportValues(imports);
        }
        return builder.build();
    }
}
