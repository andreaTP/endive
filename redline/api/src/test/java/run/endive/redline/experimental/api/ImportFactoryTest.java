package run.endive.redline.experimental.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import run.endive.runtime.ByteBufferMemory;
import run.endive.runtime.TableInstance;
import run.endive.wasm.types.MemoryLimits;
import run.endive.wasm.types.MutabilityType;
import run.endive.wasm.types.Table;
import run.endive.wasm.types.TableLimits;
import run.endive.wasm.types.ValType;
import run.endive.wasm.types.Value;

/**
 * With no native provider the factory has to hand back the ordinary runtime types,
 * which is what a platform redline does not compile for ends up running.
 */
public class ImportFactoryTest {

    private static final ImportFactory BYTECODE = ImportFactory.forBytecode();

    @Test
    public void withoutAProviderItBuildsTheBytecodeTypes() {
        assertFalse(BYTECODE.isNative());
        assertInstanceOf(ByteBufferMemory.class, BYTECODE.memory(new MemoryLimits(1, 2)));
        assertInstanceOf(
                TableInstance.class,
                BYTECODE.table(
                        new Table(ValType.FuncRef, new TableLimits(1, 1)), Value.REF_NULL_VALUE));
    }

    @Test
    public void aModuleWithoutNativeCodeGetsTheBytecodeTypes() {
        assertFalse(ImportFactory.forNativeCode(null).isNative());
    }

    @Test
    public void aBytecodeGlobalKeepsItsValueAndMutability() {
        var global = BYTECODE.global(Value.i32(7), MutabilityType.Var);

        assertEquals(7, global.getValue());
        assertEquals(MutabilityType.Var, global.getMutabilityType());
        assertEquals(ValType.I32, global.getType());
    }

    @Test
    public void aBytecodeTableStartsOnItsInitialiser() {
        var table =
                BYTECODE.table(
                        new Table(ValType.FuncRef, new TableLimits(2, 2)), Value.REF_NULL_VALUE);

        assertEquals(2, table.size());
        assertEquals(Value.REF_NULL_VALUE, table.ref(0));
    }
}
