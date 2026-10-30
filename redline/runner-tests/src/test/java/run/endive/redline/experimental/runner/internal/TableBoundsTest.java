package run.endive.redline.experimental.runner.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import run.endive.corpus.CorpusResources;
import run.endive.runtime.Instance;
import run.endive.runtime.Store;
import run.endive.testing.NativeInstanceBuilder;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmEngineException;
import run.endive.wasm.types.Value;

/** Compiled table code reaches the native buffer directly, so nothing may take it past its end. */
public class TableBoundsTest {

    @Test
    public void nativeGrowStopsAtTheBuffer() {
        try (var instance = nativeInstance("table-bounds-grow", new Store())) {
            assertEquals(-1, grow(instance, "growUnbounded", 2_000_000));
            assertEquals(-1, grow(instance, "growBounded", 2_000_000));
            assertEquals(1, grow(instance, "growUnbounded", 10));
            assertEquals(11, instance.table(0).size());
        }
    }

    @Test
    public void hostGrowDoesNotOverflow() {
        try (var instance = nativeInstance("table-bounds-grow", new Store())) {
            var table = instance.table(0);
            assertEquals(-1, table.grow(Integer.MAX_VALUE, Value.REF_NULL_VALUE, instance));
            assertEquals(1, table.size());
        }
    }

    @Test
    public void aTableDeclaredWithMaxZeroNeverGrows() {
        try (var instance = nativeInstance("table-bounds-max-zero", new Store())) {
            assertEquals(-1, grow(instance, "grow", 1));
            var table = instance.table(0);
            assertEquals(-1, table.grow(1, Value.REF_NULL_VALUE, instance));
            assertEquals(0, table.size());
        }
    }

    @Test
    public void settingAnotherInstancesFunctionIdTraps() {
        var store = new Store();
        try (var owner = nativeInstance("table-bounds-owner", store)) {
            store.register("owner", owner);
            try (var copier = nativeInstance("table-bounds-copier", store)) {
                var e =
                        assertThrows(
                                WasmEngineException.class, () -> copier.export("copy").apply());
                assertTrue(e.getMessage().contains("undefined element"), e.getMessage());
            }
        }
    }

    @Test
    public void aGrowWhoseFillFailsLeavesNullEntries() {
        var store = new Store();
        try (var owner = nativeInstance("table-bounds-owner", store)) {
            store.register("owner", owner);
            try (var grower = nativeInstance("table-bounds-foreign-grow", store)) {
                var e =
                        assertThrows(
                                WasmEngineException.class, () -> grower.export("grow").apply());
                assertTrue(e.getMessage().contains("undefined element"), e.getMessage());
                // an unwritten slot would run $local instead
                assertThrows(WasmEngineException.class, () -> grower.export("callGrown").apply());
            }
        }
    }

    @Test
    public void hostWritesOfAnUnknownFunctionIdThrow() {
        try (var owner = nativeInstance("table-bounds-owner", new Store())) {
            var e =
                    assertThrows(
                            WasmEngineException.class, () -> owner.table(0).setRef(0, 99, owner));
            assertTrue(e.getMessage().contains("undefined element"), e.getMessage());
        }
    }

    private static int grow(Instance instance, String export, int delta) {
        return (int) instance.export(export).apply(delta)[0];
    }

    private static Instance nativeInstance(String name, Store store) {
        var module = Parser.parse(CorpusResources.getResource("compiled/" + name + ".wat.wasm"));
        return NativeInstanceBuilder.builder(module)
                .withImportValues(store.toImportValues())
                .build();
    }
}
