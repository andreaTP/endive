package run.endive.redline.experimental.runner.jffi.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import run.endive.corpus.CorpusResources;
import run.endive.redline.experimental.api.NativeCode;
import run.endive.redline.experimental.api.internal.RedlineTarget;
import run.endive.redline.experimental.compiler.internal.NativeCompiler;
import run.endive.redline.experimental.runner.jffi.JffiNativeMachineFactory;
import run.endive.runtime.HostFunction;
import run.endive.runtime.Instance;
import run.endive.runtime.Store;
import run.endive.testing.NativeInstanceBuilder;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmEngineException;
import run.endive.wasm.WasmModule;
import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.ValType;

/**
 * A function placed in a table runs in the instance that defined it, whichever
 * instance calls it through the table. The spec suite can't catch a runner that
 * gets this wrong, because its shared-table functions only return constants and
 * behave the same in any instance. The functions here read their own global and
 * memory, call their own import, and trap.
 *
 * <p>This is how wit-component lays out every component: a shim module's
 * functions are call_indirects through a table that a fixup module fills with
 * another instance's functions.
 */
public class CrossInstanceCallIndirectTest {

    private static final String OWNER = "cross-instance-owner";
    private static final String CALLER = "cross-instance-caller";
    private static final String HOLDER = "cross-instance-holder";
    private static final String WRITER = "cross-instance-writer";
    private static final String READER = "cross-instance-reader";
    private static final String SETTER = "cross-instance-setter";

    @Test
    public void calleeReadsItsOwnGlobal() {
        try (var linked = linkNative()) {
            assertEquals(
                    42, call(linked.caller, "call", 0), "the owner's global, not the caller's");
        }
    }

    @Test
    public void calleeReadsItsOwnMemory() {
        try (var linked = linkNative()) {
            assertEquals(
                    1000, call(linked.caller, "call", 1), "the owner's memory, not the caller's");
        }
    }

    @Test
    public void calleeWritesItsOwnGlobal() {
        try (var linked = linkNative()) {
            assertEquals(43, call(linked.caller, "call", 5));
            assertEquals(44, call(linked.caller, "call", 5));
            assertEquals(
                    44, (int) linked.owner.global(0).getValue(), "the write landed in the owner");
        }
    }

    @Test
    public void calleeCallsItsOwnImport() {
        try (var linked = linkNative()) {
            assertEquals(7, call(linked.caller, "call", 2));
        }
    }

    @Test
    public void tailCallRunsInTheOwner() {
        try (var linked = linkNative()) {
            assertEquals(42, call(linked.caller, "tailCall", 0));
            assertEquals(1000, call(linked.caller, "tailCall", 1));
        }
    }

    @Test
    public void multiValueResultsReachTheCaller() {
        try (var linked = linkNative()) {
            assertEquals(42 - 1000, (int) linked.caller.export("callPair").apply()[0]);
        }
    }

    /** A call_indirect to a multi-value host function tolerates a null result. */
    @Test
    public void multiValueHostFunctionReturningNull() {
        var none =
                new HostFunction(
                        "host",
                        "none",
                        FunctionType.of(List.of(), List.of(ValType.I32, ValType.I32)),
                        (inst, args) -> null);
        var store = new Store().addFunction(none);
        try (var owner = nativeInstance("cross-instance-null-host-owner", store)) {
            store.register("owner", owner);
            try (var caller = nativeInstance("cross-instance-null-host-caller", store)) {
                assertDoesNotThrow(() -> caller.export("call").apply());
            }
        }
    }

    @Test
    public void calleeTrapStopsTheCaller() {
        try (var linked = linkNative()) {
            var e = assertThrows(WasmEngineException.class, () -> call(linked.caller, "call", 3));
            assertTrue(e.getMessage().contains("unreachable"), e.getMessage());
        }
    }

    /** Compiled code caches the page count, so the caller must see a growth in the callee. */
    @Test
    public void callerSeesMemoryTheCalleeGrew() {
        var store = new Store();
        try (var owner = nativeInstance("cross-instance-grow-owner", store)) {
            store.register("owner", owner);
            try (var caller = nativeInstance("cross-instance-grow-caller", store)) {
                assertEquals(2, (int) caller.export("growThenStore").apply()[0]);
            }
        }
    }

    /**
     * The callee gets what is left of the caller's stack budget, not a new one,
     * or a chain of instances could outgrow the thread's stack.
     */
    @Test
    public void calleeSharesTheCallersStackBudget() {
        var store = new Store();
        try (var owner = nativeInstance("cross-instance-recurse-entry-owner", store)) {
            store.register("owner", owner);
            try (var caller = nativeInstance("cross-instance-recurse-entry-caller", store)) {
                int callerBudget = depthReached(caller, -1, caller);
                int fromTheTop = depthReached(caller, 0, owner);
                int fromHalfway = depthReached(caller, callerBudget / 2, owner);
                assertTrue(
                        fromHalfway < fromTheTop * 3 / 4,
                        fromHalfway + " levels from halfway, " + fromTheTop + " from the top");
            }
        }
    }

    @Test
    public void importedFunctionSharesTheCallersStackBudget() {
        var store = new Store();
        try (var owner = nativeInstance("cross-instance-recurse-import-owner", store)) {
            store.register("owner", owner);
            try (var caller = nativeInstance("cross-instance-recurse-import-caller", store)) {
                int callerBudget = depthReached(caller, -1, caller);
                int fromTheTop = depthReached(caller, 0, owner);
                int fromHalfway = depthReached(caller, callerBudget / 2, owner);
                assertTrue(
                        fromHalfway < fromTheTop * 3 / 4,
                        fromHalfway + " levels from halfway, " + fromTheTop + " from the top");
            }
        }
    }

    @Test
    public void signatureIsCheckedByStructure() {
        try (var linked = linkNative()) {
            var e =
                    assertThrows(
                            WasmEngineException.class,
                            () -> linked.caller.export("wrongType").apply());
            assertTrue(e.getMessage().contains("indirect call type mismatch"), e.getMessage());
        }
    }

    /** As in the interpreter, a function whose declared type is a subtype matches. */
    @Test
    public void signatureAcceptsADeclaredSubtype() {
        var store = new Store();
        try (var owner = nativeInstance("cross-instance-subtype-owner", store)) {
            store.register("owner", owner);
            try (var caller = nativeInstance("cross-instance-subtype-caller", store)) {
                assertEquals(0, (int) caller.export("call").apply()[0]);
            }
        }
    }

    @Test
    public void tableReportsTheOwnerOfEachEntry() {
        try (var linked = linkNative()) {
            assertSame(linked.owner, linked.owner.table(0).instance(0));
        }
    }

    @Test
    public void nullEntryTraps() {
        try (var holder = nativeInstance(HOLDER, new Store())) {
            var e = assertThrows(WasmEngineException.class, () -> call(holder, "call", 0));
            assertTrue(e.getMessage().contains("uninitialized element"), e.getMessage());
        }
    }

    /**
     * The table belongs to a native instance and an interpreted instance fills
     * it with its own functions, so the entries have no native address to call.
     */
    @Test
    public void entriesFromAnInterpretedInstance() {
        var store = new Store();
        try (var holder = nativeInstance("cross-instance-holder-with-entry", store)) {
            store.register("holder", holder);
            try (var filler = interpretedInstance(WRITER, store)) {
                assertEquals(42, call(holder, "call", 0), "the interpreted filler's function");
                assertEquals(-1, call(holder, "call", 1), "the holder's own function");
                assertSame(filler, holder.table(0).instance(0));
            }
        }
    }

    /** The interpreter learns the native owner of an entry from the table. */
    @Test
    public void interpretedCallerRunsTheNativeOwner() {
        var store = storeWithHost();
        try (var owner = nativeInstance(OWNER, store)) {
            store.register("owner", owner);
            try (var caller = interpretedInstance(CALLER, store)) {
                assertEquals(42, call(caller, "call", 0));
                assertEquals(1000, call(caller, "call", 1));
                assertEquals(7, call(caller, "call", 2));
            }
        }
    }

    /** No one calls the defining instance, so only the table says it owns the entries. */
    @Test
    public void tableInitialiserBelongsToItsDefiningInstance() {
        var store = new Store();
        try (var definer = nativeInstance("cross-instance-definer", store)) {
            store.register("holder", definer);
            try (var reader = nativeInstance(READER, store)) {
                assertEquals(42, call(reader, "call", 1), "the definer's function");
                assertSame(definer, definer.table(0).instance(1));
            }
        }
    }

    /** Entries copied out of an imported table still run in their owner. */
    @Test
    public void copiedEntriesKeepTheirNativeOwner() {
        var store = storeWithHost();
        try (var owner = nativeInstance(OWNER, store)) {
            store.register("owner", owner);
            try (var copier = nativeInstance("cross-instance-copier", store)) {
                copier.export("copy").apply();
                assertEquals(42, call(copier, "call", 0));
                assertEquals(1000, call(copier, "call", 1));
                assertSame(owner, copier.table(1).instance(0));
            }
        }
    }

    /** Copies within and across tables keep the owners an interpreted instance wrote. */
    @Test
    public void copiedEntriesKeepTheirInterpretedOwner() {
        var store = new Store();
        try (var holder = nativeInstance("cross-instance-two-tables", store)) {
            store.register("holder", holder);
            try (var filler = interpretedInstance(WRITER, store)) {
                holder.export("copyAToB").apply();
                assertEquals(42, call(holder, "callB", 0));
                assertSame(filler, holder.table(1).instance(0));

                holder.export("shiftA").apply();
                assertEquals(42, call(holder, "callA", 0));
                assertEquals(42, call(holder, "callA", 1));
                assertSame(filler, holder.table(0).instance(1));
                assertNull(holder.table(0).instance(2), "shifted in from a null entry");
            }
        }
    }

    @Test
    public void hostWritesRecordTheOwner() {
        var store = storeWithHost();
        try (var owner = nativeInstance(OWNER, store);
                var holder = nativeInstance(HOLDER, store)) {
            var table = holder.table(0);
            table.setRef(0, 1, owner); // $readGlobal
            assertEquals(42, call(holder, "call", 0));
            assertEquals(2, table.grow(1, 2, owner)); // $readMemory
            assertEquals(1000, call(holder, "call", 2));
            assertSame(owner, table.instance(2));
        }
    }

    /** table.set, table.grow and table.fill run natively in the instance writing the entry. */
    @Test
    public void nativeWritesByAnotherInstance() {
        var store = new Store();
        try (var holder = nativeInstance(HOLDER, store)) {
            store.register("holder", holder);
            try (var setter = nativeInstance(SETTER, store)) {
                setter.export("set").apply(0);
                assertEquals(42, call(holder, "call", 0));
                assertSame(setter, holder.table(0).instance(0));

                assertEquals(2, (int) setter.export("grow").apply()[0]);
                assertEquals(42, call(holder, "call", 2));

                setter.export("fill").apply();
                assertEquals(42, call(holder, "call", 1));
            }
        }
    }

    /** A new instance may get a closed owner's context address, and must not take its entries. */
    @Test
    public void closedOwnerTrapsInsteadOfRunningAnotherInstance() {
        var store = new Store();
        try (var holder = nativeInstance(HOLDER, store)) {
            store.register("holder", holder);
            var writer = nativeInstance(WRITER, store);
            assertEquals(42, call(holder, "call", 0));
            writer.close();

            try (var reader = nativeInstance(READER, store);
                    var interpretedReader = interpretedInstance(READER, store)) {
                assertSame(writer, holder.table(0).instance(0));
                assertClosed(() -> call(reader, "call", 0));
                assertClosed(() -> call(holder, "call", 0));
                assertClosed(() -> call(interpretedReader, "call", 0));
            }
        }
    }

    /** The table drops closed instances that none of its entries refer to. */
    @Test
    public void tableReleasesClosedWriters() {
        var store = new Store();
        try (var holder = nativeInstance(HOLDER, store)) {
            store.register("holder", holder);
            for (int i = 0; i < 50; i++) {
                nativeInstance(WRITER, store).close();
            }
            var table = (JffiNativeTable) holder.table(0);
            assertTrue(table.writerCount() <= 8, table.writerCount() + " writers kept");
            assertClosed(() -> call(holder, "call", 0));
        }
    }

    @Test
    public void aPrunedWriterRegistersAgain() {
        var store = new Store();
        try (var holder = nativeInstance(HOLDER, store)) {
            store.register("holder", holder);
            try (var setter = nativeInstance(SETTER, store)) {
                setter.export("set").apply(0);
                // enough writers over the same entry for the table to prune the setter
                for (int i = 0; i < 20; i++) {
                    nativeInstance(WRITER, store);
                }
                setter.export("set").apply(0);
                assertEquals(42, call(holder, "call", 0));
                assertSame(setter, holder.table(0).instance(0));
            }
        }
    }

    /** An image may leave functions out, which a table must not jump to. */
    @Test
    public void uncompiledFunctionIsNotCalled() {
        var module = module("cross-instance-uncompiled");
        try (var instance =
                JffiNativeMachineFactory.builder(module)
                        .withPrecompiledCode(withoutFirstBody(compile(module)))
                        .build()) {
            var e =
                    assertThrows(
                            WasmEngineException.class, () -> instance.export("callElem").apply());
            assertTrue(e.getMessage().contains("not compiled"), e.getMessage());
            e =
                    assertThrows(
                            WasmEngineException.class, () -> instance.export("setAndCall").apply());
            assertTrue(e.getMessage().contains("uninitialized element"), e.getMessage());
        }
    }

    /**
     * Has the caller descend {@code levels} before calling the owner, until
     * the stack runs out.
     *
     * @return how many levels {@code counted} recursed
     */
    private static int depthReached(Instance caller, int levels, Instance counted) {
        var depth = counted.exports().global("depth");
        int before = (int) depth.getValue();
        var e = assertThrows(WasmEngineException.class, () -> call(caller, "descend", levels));
        assertTrue(e.getMessage().contains("call stack exhausted"), e.getMessage());
        return (int) depth.getValue() - before;
    }

    private static void assertClosed(Runnable call) {
        var e = assertThrows(WasmEngineException.class, call::run);
        assertTrue(e.getMessage().contains("instance is closed"), e.getMessage());
    }

    /** The owner and caller instances, closed caller first. */
    private static final class Linked implements AutoCloseable {
        final Instance owner;
        final Instance caller;

        Linked(Instance owner, Instance caller) {
            this.owner = owner;
            this.caller = caller;
        }

        @Override
        public void close() {
            caller.close();
            owner.close();
        }
    }

    /** Instantiates the owner and the caller natively, with the caller importing its table. */
    private static Linked linkNative() {
        var store = storeWithHost();
        var owner = nativeInstance(OWNER, store);
        store.register("owner", owner);
        var caller = nativeInstance(CALLER, store);
        return new Linked(owner, caller);
    }

    /** A store with the owner's host import, which returns 7. */
    private static Store storeWithHost() {
        var seven =
                new HostFunction(
                        "host",
                        "seven",
                        FunctionType.of(List.of(), List.of(ValType.I32)),
                        (inst, args) -> new long[] {7});
        return new Store().addFunction(seven);
    }

    /** Calls an export that takes a table index and returns an i32. */
    private static int call(Instance instance, String export, int index) {
        return (int) instance.export(export).apply(index)[0];
    }

    private static Instance nativeInstance(String name, Store store) {
        return NativeInstanceBuilder.builder(module(name))
                .withImportValues(store.toImportValues())
                .build();
    }

    private static Instance interpretedInstance(String name, Store store) {
        return Instance.builder(module(name)).withImportValues(store.toImportValues()).build();
    }

    private static WasmModule module(String name) {
        return Parser.parse(CorpusResources.getResource("compiled/" + name + ".wat.wasm"));
    }

    private static NativeCode compile(WasmModule module) {
        return NativeCompiler.compile(RedlineTarget.detectHost().orElseThrow().triple(), module);
    }

    /** Returns {@code code} with its first function body left uncompiled. */
    private static NativeCode withoutFirstBody(NativeCode code) {
        int[] bodies = new int[code.functionBodyCount()];
        int[] entries = new int[code.functionBodyCount()];
        for (int i = 0; i < bodies.length; i++) {
            bodies[i] = i == 0 ? NativeCode.NOT_COMPILED : code.bodyOffset(i);
            entries[i] = i == 0 ? NativeCode.NOT_COMPILED : code.entryTrampolineOffset(i);
        }
        int[] importTrampolines = new int[code.importCount()];
        int[] importSlots = new int[code.importCount()];
        for (int i = 0; i < importTrampolines.length; i++) {
            importTrampolines[i] = code.importTrampolineOffset(i);
            importSlots[i] = code.importStubSlotOffset(i);
        }
        return new NativeCode(
                code.triple(), code.image(), bodies, entries, importTrampolines, importSlots);
    }
}
