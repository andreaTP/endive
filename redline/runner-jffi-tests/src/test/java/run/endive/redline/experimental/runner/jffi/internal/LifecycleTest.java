package run.endive.redline.experimental.runner.jffi.internal;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import run.endive.corpus.CorpusResources;
import run.endive.redline.experimental.api.ImportFactory;
import run.endive.redline.experimental.api.NativeCode;
import run.endive.redline.experimental.api.internal.NativeFootprint;
import run.endive.redline.experimental.api.internal.RedlineTarget;
import run.endive.redline.experimental.compiler.internal.NativeCompiler;
import run.endive.redline.experimental.runner.jffi.JffiMachineFactoryProvider;
import run.endive.redline.experimental.runner.jffi.JffiNativeMachineFactory;
import run.endive.runtime.GlobalInstance;
import run.endive.runtime.HostFunction;
import run.endive.runtime.ImportGlobal;
import run.endive.runtime.ImportMemory;
import run.endive.runtime.ImportTable;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.runtime.Memory;
import run.endive.runtime.TableInstance;
import run.endive.wasm.Parser;
import run.endive.wasm.UninstantiableException;
import run.endive.wasm.WasmEngineException;
import run.endive.wasm.WasmModule;
import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.MemoryLimits;
import run.endive.wasm.types.MutabilityType;
import run.endive.wasm.types.Table;
import run.endive.wasm.types.TableLimits;
import run.endive.wasm.types.ValType;
import run.endive.wasm.types.Value;

/**
 * Native state lives as long as the Java objects that use it, as on the heap: the garbage
 * collector frees it once nothing reaches it. close() only stops the instance.
 */
public class LifecycleTest {

    @Test
    public void closingTwiceIsSafe() {
        var instance = build(parse("trap-stops-execution"), ImportValues.empty());
        instance.close();
        instance.close();
    }

    @Test
    public void aMemoryTheModuleDefinesWorks() {
        try (var instance = build(parse("trap-stops-execution"), ImportValues.empty())) {
            instance.memory().writeI32(0, 0x5A5A5A5A);
            assertEquals(0x5A5A5A5A, instance.memory().readInt(0));
        }
    }

    /** Issue #203: instances nobody closes used to keep their native state forever. */
    @Test
    public void droppedInstancesAreFreed() throws InterruptedException {
        var withMemory = parse("trap-stops-execution");
        var withTable = parse("big-table");
        var withMemoryCode = compile(withMemory);
        var withTableCode = compile(withTable);
        long before = settledCount();
        assertCountsWhileReachable(withMemory, withMemoryCode, before);
        for (int i = 0; i < 100; i++) {
            build(withMemory, withMemoryCode).memory().writeI32(0, i);
            build(withTable, withTableCode).export("noop").apply();
        }
        awaitFreed(before);
    }

    // so a test that waits for the count to drop back can't pass by never counting
    private static void assertCountsWhileReachable(
            WasmModule module, NativeCode code, long before) {
        var instance = build(module, code);
        assertTrue(NativeFootprint.live() > before, "nothing was counted");
        instance.close();
    }

    /** Host code keeps using what it holds while the instances that made it are collected. */
    @Test
    public void heldMemoryAndTablesSurviveCollection() throws InterruptedException {
        var withMemory = parse("trap-stops-execution");
        var withTable = parse("all-exports");
        var memoryCode = compile(withMemory);
        var tableCode = compile(withTable);
        var running = new AtomicBoolean(true);
        // pauses between collections, or the test thread hardly runs between them
        var collector =
                new Thread(
                        () -> {
                            while (running.get()) {
                                System.gc();
                                LockSupport.parkNanos(MILLISECONDS.toNanos(5));
                            }
                        });
        collector.setDaemon(true);
        collector.start();
        try {
            for (int i = 0; i < 3; i++) {
                var held = holdMemoryAndTable(withMemory, memoryCode, withTable, tableCode);
                long deadline = System.nanoTime() + SECONDS.toNanos(30);
                int j = 0;
                // until both instances are gone, so the accesses run while they are collected
                while (held.memoryOwner.get() != null || held.tableOwner.get() != null) {
                    assertTrue(System.nanoTime() < deadline, "the instances were never collected");
                    held.memory.writeI32((j % 1024) * 4, j);
                    assertEquals(j, held.memory.readInt((j % 1024) * 4));
                    assertEquals(10, held.table.size());
                    j++;
                }
                held.memory.writeI32(0, i);
                assertEquals(i, held.memory.readInt(0));
                assertEquals(10, held.table.size());
            }
        } finally {
            running.set(false);
            collector.join();
        }
    }

    /** A memory and a table the host holds, and weak references to the instances that made them. */
    private static final class Held {
        final Memory memory;
        final TableInstance table;
        final WeakReference<Instance> memoryOwner;
        final WeakReference<Instance> tableOwner;

        Held(Instance memoryOwner, Instance tableOwner) {
            this.memory = memoryOwner.memory();
            this.table = tableOwner.table(0);
            this.memoryOwner = new WeakReference<>(memoryOwner);
            this.tableOwner = new WeakReference<>(tableOwner);
        }
    }

    private static Held holdMemoryAndTable(
            WasmModule withMemory,
            NativeCode memoryCode,
            WasmModule withTable,
            NativeCode tableCode) {
        return new Held(build(withMemory, memoryCode), build(withTable, tableCode));
    }

    @Test
    public void closedInstancesAreFreedOnceUnreachable() throws InterruptedException {
        var module = parse("trap-stops-execution");
        var code = compile(module);
        long before = settledCount();
        for (int i = 0; i < 100; i++) {
            try (var instance = build(module, code)) {
                instance.memory().writeI32(0, i);
            }
        }
        awaitFreed(before);
    }

    /** The upcall stubs only hold their machine weakly, or nothing could ever free it. */
    @Test
    public void anInstanceThatCalledTheHostIsCollected() throws InterruptedException {
        awaitCollected(instanceThatCalledTheHost());
    }

    private static WeakReference<Instance> instanceThatCalledTheHost() {
        var imports = ImportValues.builder().addFunction(noop("boom")).build();
        var instance = build(parse("host-throw-stops-execution"), imports);
        instance.export("callBoom").apply();
        assertEquals(42, instance.memory().readInt(0));
        return new WeakReference<>(instance);
    }

    @Test
    public void aClosedInstanceRefusesCallsButItsMemoryStaysUsable() {
        var instance = build(parse("trap-stops-execution"), ImportValues.empty());
        var memory = instance.memory();
        instance.close();

        var e =
                assertThrows(
                        WasmEngineException.class, () -> instance.export("storeAfterTrap").apply());
        assertTrue(e.getMessage().contains("instance is closed"), e.getMessage());
        memory.writeI32(0, 7);
        assertEquals(7, memory.readInt(0));
    }

    /** A host function may close the instance that called it; the call still finishes. */
    @Test
    public void closingFromInsideACallLetsItFinish() {
        Instance[] self = new Instance[1];
        var imports =
                ImportValues.builder()
                        .addFunction(
                                new HostFunction(
                                        "host",
                                        "boom",
                                        FunctionType.of(List.of(), List.of()),
                                        (inst, args) -> {
                                            self[0].close();
                                            return null;
                                        }))
                        .build();
        var instance = build(parse("host-throw-stops-execution"), imports);
        self[0] = instance;

        instance.export("callBoom").apply();
        assertEquals(42, instance.memory().readInt(0), "the call ran to the end");
        assertThrows(WasmEngineException.class, () -> instance.export("callBoom").apply());
    }

    @Test
    public void closingWhileAnotherThreadIsInACallLetsItFinish() throws InterruptedException {
        var running = new CountDownLatch(1);
        var noParams = FunctionType.of(List.of(), List.of());
        var imports =
                ImportValues.builder()
                        .addFunction(
                                new HostFunction("host", "reenter", noParams, (inst, args) -> null))
                        .addFunction(
                                new HostFunction(
                                        "host", "raiseFlag", noParams, (inst, args) -> null))
                        .addFunction(
                                new HostFunction(
                                        "host",
                                        "tick",
                                        noParams,
                                        (inst, args) -> {
                                            running.countDown();
                                            return null;
                                        }))
                        .build();
        var instance = build(parse("reentrant-interrupt"), imports);
        var caller = new Thread(() -> instance.export("spin").apply());
        caller.setDaemon(true);
        caller.setUncaughtExceptionHandler((t, e) -> {});
        caller.start();
        assertTrue(running.await(10, SECONDS), "the call never started");

        instance.close();
        assertTrue(caller.isAlive(), "close() stops new calls, not a running one");

        caller.interrupt();
        caller.join(SECONDS.toMillis(10));
        assertFalse(caller.isAlive(), "the call was not interrupted");
        assertThrows(WasmEngineException.class, () -> instance.export("spin").apply());
    }

    @Test
    public void globalsKeepTheirValueAfterClose() {
        GlobalInstance exported;
        try (var instance = build(parse("all-exports"), ImportValues.empty())) {
            exported = instance.exports().global("glob1");
        }
        assertEquals(42L, exported.getValue());

        var counter =
                JffiNativeMachineFactory.createImportGlobal(10L, ValType.I32, MutabilityType.Var);
        try (var instance = buildImportedMutables(memory(), table(), counter)) {
            instance.export("run").apply();
        }
        assertEquals(11L, counter.getValue(), "the host's global outlives the instance");
        try (var instance = buildImportedMutables(memory(), table(), counter)) {
            instance.export("run").apply();
        }
        assertEquals(12L, counter.getValue(), "and a new instance can import it again");
    }

    /** A host global sits in its instance's globals buffer, but keeps only that alive. */
    @Test
    public void aHostGlobalDoesNotKeepItsInstanceAlive() throws InterruptedException {
        var counter =
                JffiNativeMachineFactory.createImportGlobal(10L, ValType.I32, MutabilityType.Var);
        awaitCollected(instanceThatAdopted(counter));
        assertEquals(11L, counter.getValue());
        counter.setValue(20L);
        assertEquals(20L, counter.getValue());
    }

    private static WeakReference<Instance> instanceThatAdopted(GlobalInstance counter) {
        var instance = buildImportedMutables(memory(), table(), counter);
        instance.export("run").apply();
        return new WeakReference<>(instance);
    }

    @Test
    public void instancesFromOneBuilderKeepTheirOwnGlobals() {
        var module = parse("exported-mutable-global");
        var builder =
                JffiNativeMachineFactory.builder(module)
                        .withPrecompiledCode(compile(module))
                        .toInstanceBuilder();
        var first = builder.build();
        var second = builder.build();

        assertEquals(1L, first.export("increment").apply()[0]);
        assertEquals(1L, second.export("increment").apply()[0]);
        assertEquals(2L, first.export("increment").apply()[0]);
        first.close();
        assertEquals(2L, second.export("increment").apply()[0]);
        second.close();
    }

    /** Closing in any order works: what one instance imports stays alive while it uses it. */
    @Test
    public void anExportedMemoryOutlivesItsClosedExporter() throws InterruptedException {
        var exporter = build(parse("trap-stops-execution"), ImportValues.empty());
        var importer = buildImportedMutables(exporter.memory(), table(), global());
        exporter.close();
        var dropped = new WeakReference<>(exporter);
        exporter = null;
        awaitCollected(dropped);

        importer.export("run").apply();
        assertEquals(23130, importer.memory().readInt(0));
        importer.close();
    }

    @Test
    public void anExportedTableOutlivesItsClosedExporter() {
        var exporter = build(parse("call_indirect-export"), ImportValues.empty());
        var table = exporter.exports().table("shared-table");
        var imports =
                ImportValues.builder()
                        .addTable(new ImportTable("test", "shared-table", table))
                        .build();
        exporter.close();

        try (var importer = build(parse("call_indirect-import"), imports)) {
            assertEquals(5, table.size());
            assertEquals(importer, table.instance(1));
        }
    }

    @Test
    public void importsAreFreedOnceUnused() throws InterruptedException {
        long before = settledCount();
        useImportsOnce();
        awaitFreed(before);
    }

    private static void useImportsOnce() {
        var imports = ImportFactory.forNative(new JffiMachineFactoryProvider());
        var memory = imports.memory(new MemoryLimits(1, 1));
        var table = imports.table(tableType(), Value.REF_NULL_VALUE);
        var counter = imports.global(Value.i32(10), MutabilityType.Var);
        try (var instance = buildImportedMutables(memory, table, counter)) {
            instance.export("run").apply();
        }
        assertEquals(23130, memory.readInt(0), "the memory outlives the instance");
        assertEquals(11L, counter.getValue());
    }

    @Test
    public void aFailedBuildIsFreed() throws InterruptedException {
        var module = parse("failing-start-imported-memory");
        var code = compile(module);
        long before = settledCount();
        for (int i = 0; i < 10; i++) {
            var values =
                    ImportValues.builder()
                            .addMemory(
                                    new ImportMemory(
                                            "env",
                                            "memory",
                                            JffiNativeMachineFactory.createMemory(
                                                    new MemoryLimits(1, 1))))
                            .build();
            assertThrows(UninstantiableException.class, () -> build(module, code, values));
        }
        awaitFreed(before);
    }

    /** The spec keeps a failed build's functions in a table callable, for as long as it lives. */
    @Test
    public void aFailedBuildInATableLastsAsLongAsTheTable() throws InterruptedException {
        long before = settledCount();
        var exporter = build(parse("call_indirect-export"), ImportValues.empty());
        failToBuildIntoTheTableOf(exporter);

        settledCount();
        assertEquals(7L, exporter.export("call-other").apply()[0], "the failed build's function");
        exporter.close();
        exporter = null;
        awaitFreed(before);
    }

    private static void failToBuildIntoTheTableOf(Instance exporter) {
        var memory = JffiNativeMachineFactory.createMemory(new MemoryLimits(1, 1));
        memory.writeI32(0, 7);
        var values =
                ImportValues.builder()
                        .addTable(
                                new ImportTable(
                                        "test",
                                        "shared-table",
                                        exporter.exports().table("shared-table")))
                        .addMemory(new ImportMemory("env", "memory", memory))
                        .build();
        assertThrows(
                UninstantiableException.class,
                () -> build(parse("failing-start-table-writer"), values));
    }

    /** Overwriting the entries that held a dropped instance lets the table let it go. */
    @Test
    public void aTableDropsWritersNoEntryNames() throws InterruptedException {
        var holder = build(parse("call_indirect-export"), ImportValues.empty());
        var first = writeIntoTheTableOf(holder);
        for (int i = 0; i < 20; i++) {
            writeIntoTheTableOf(holder);
        }
        awaitCollected(first);
        holder.close();
    }

    private static WeakReference<Instance> writeIntoTheTableOf(Instance holder) {
        var imports =
                ImportValues.builder()
                        .addTable(
                                new ImportTable(
                                        "test",
                                        "shared-table",
                                        holder.exports().table("shared-table")))
                        .build();
        return new WeakReference<>(build(parse("call_indirect-import"), imports));
    }

    @Test
    public void aBuildThatFailsBeforeItsMachineFreesItsMemory() throws InterruptedException {
        var withMemory = parse("trap-stops-execution");
        var otherModulesCode = compile(parse("big-table"));
        long before = settledCount();

        assertThrows(WasmEngineException.class, () -> build(withMemory, otherModulesCode));
        awaitFreed(before);
    }

    @Test
    public void aTableOutlivesItsClosedInstance() {
        TableInstance table;
        try (var instance = build(parse("big-table"), ImportValues.empty())) {
            table = instance.table(0);
        }
        assertEquals(100000, table.size());
    }

    /** Frees earlier tests' garbage, so a count taken now is a stable baseline. */
    private static long settledCount() throws InterruptedException {
        long previous = Long.MAX_VALUE;
        int stable = 0;
        long deadline = System.nanoTime() + SECONDS.toNanos(30);
        while (stable < 3 && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(20);
            long live = NativeFootprint.live();
            stable = live == previous ? stable + 1 : 0;
            previous = live;
        }
        return previous;
    }

    private static void awaitFreed(long baseline) throws InterruptedException {
        awaitTrue(
                () -> NativeFootprint.live() <= baseline,
                () -> (NativeFootprint.live() - baseline) + " native bytes still live");
    }

    private static void awaitCollected(WeakReference<?> reference) throws InterruptedException {
        awaitTrue(() -> reference.get() == null, () -> "still reachable: " + reference.get());
    }

    private static void awaitTrue(BooleanSupplier condition, Supplier<String> why)
            throws InterruptedException {
        long deadline = System.nanoTime() + SECONDS.toNanos(30);
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, why);
            System.gc();
            Thread.sleep(10);
        }
    }

    private static HostFunction noop(String name) {
        return new HostFunction(
                "host", name, FunctionType.of(List.of(), List.of()), (inst, args) -> null);
    }

    private static WasmModule parse(String name) {
        return Parser.parse(CorpusResources.getResource("compiled/" + name + ".wat.wasm"));
    }

    private static NativeCode compile(WasmModule module) {
        return NativeCompiler.compile(RedlineTarget.detectHost().orElseThrow().triple(), module);
    }

    private static Instance build(WasmModule module, ImportValues imports) {
        return build(module, compile(module), imports);
    }

    private static Instance build(WasmModule module, NativeCode code) {
        return build(module, code, ImportValues.empty());
    }

    private static Instance build(WasmModule module, NativeCode code, ImportValues imports) {
        return JffiNativeMachineFactory.builder(module)
                .withImportValues(imports)
                .withPrecompiledCode(code)
                .build();
    }

    private static Instance buildImportedMutables(
            Memory memory, TableInstance table, GlobalInstance counter) {
        var imports =
                ImportValues.builder()
                        .addMemory(new ImportMemory("env", "memory", memory))
                        .addTable(new ImportTable("env", "table", table))
                        .addGlobal(new ImportGlobal("env", "counter", counter))
                        .build();
        return build(parse("imported-mutables"), imports);
    }

    private static Memory memory() {
        return JffiNativeMachineFactory.createMemory(new MemoryLimits(1, 1));
    }

    private static TableInstance table() {
        return JffiNativeMachineFactory.createImportTable(tableType(), Value.REF_NULL_VALUE);
    }

    private static GlobalInstance global() {
        return JffiNativeMachineFactory.createImportGlobal(10L, ValType.I32, MutabilityType.Var);
    }

    private static Table tableType() {
        return new Table(ValType.FuncRef, new TableLimits(2, 2));
    }
}
