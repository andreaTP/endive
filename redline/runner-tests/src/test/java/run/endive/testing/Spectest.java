package run.endive.testing;

import static run.endive.wasm.types.Value.REF_NULL_VALUE;

import java.util.List;
import run.endive.redline.experimental.runner.NativeMachineFactory;
import run.endive.runtime.HostFunction;
import run.endive.runtime.ImportGlobal;
import run.endive.runtime.ImportMemory;
import run.endive.runtime.ImportTable;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.runtime.WasmFunctionHandle;
import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.MemoryLimits;
import run.endive.wasm.types.MutabilityType;
import run.endive.wasm.types.Table;
import run.endive.wasm.types.TableLimits;
import run.endive.wasm.types.ValType;
import run.endive.wasm.types.Value;

public final class Spectest {
    private static final WasmFunctionHandle noop = (Instance instance, long... args) -> null;

    private Spectest() {}

    public static ImportValues toImportValues() {
        return ImportValues.builder()
                .addFunction(new HostFunction("spectest", "print", FunctionType.empty(), noop))
                .addFunction(
                        new HostFunction(
                                "spectest",
                                "print_i32",
                                FunctionType.of(List.of(ValType.I32), List.of()),
                                noop))
                .addFunction(
                        new HostFunction(
                                "spectest",
                                "print_i32_1",
                                FunctionType.of(List.of(ValType.I32), List.of()),
                                noop))
                .addFunction(
                        new HostFunction(
                                "spectest",
                                "print_i32_2",
                                FunctionType.of(List.of(ValType.I32), List.of()),
                                noop))
                .addFunction(
                        new HostFunction(
                                "spectest",
                                "print_f32",
                                FunctionType.of(List.of(ValType.F32), List.of()),
                                noop))
                .addFunction(
                        new HostFunction(
                                "spectest",
                                "print_i32_f32",
                                FunctionType.of(List.of(ValType.I32, ValType.F32), List.of()),
                                noop))
                .addFunction(
                        new HostFunction(
                                "spectest",
                                "print_i64",
                                FunctionType.of(List.of(ValType.I64), List.of()),
                                noop))
                .addFunction(
                        new HostFunction(
                                "spectest",
                                "print_i64_1",
                                FunctionType.of(List.of(ValType.I64), List.of()),
                                noop))
                .addFunction(
                        new HostFunction(
                                "spectest",
                                "print_i64_2",
                                FunctionType.of(List.of(ValType.I64), List.of()),
                                noop))
                .addFunction(
                        new HostFunction(
                                "spectest",
                                "print_f64",
                                FunctionType.of(List.of(ValType.F64), List.of()),
                                noop))
                .addFunction(
                        new HostFunction(
                                "spectest",
                                "print_f64_f64",
                                FunctionType.of(List.of(ValType.F64, ValType.F64), List.of()),
                                noop))
                .addGlobal(
                        new ImportGlobal(
                                "spectest",
                                "global_i32",
                                NativeMachineFactory.createImportGlobal(
                                        Value.i32(666).raw(), ValType.I32, MutabilityType.Const)))
                .addGlobal(
                        new ImportGlobal(
                                "spectest",
                                "global_i64",
                                NativeMachineFactory.createImportGlobal(
                                        Value.i64(666).raw(), ValType.I64, MutabilityType.Const)))
                .addGlobal(
                        new ImportGlobal(
                                "spectest",
                                "global_f32",
                                NativeMachineFactory.createImportGlobal(
                                        Value.fromFloat(666.6f).raw(),
                                        ValType.F32,
                                        MutabilityType.Const)))
                .addGlobal(
                        new ImportGlobal(
                                "spectest",
                                "global_f64",
                                NativeMachineFactory.createImportGlobal(
                                        Value.fromDouble(666.6).raw(),
                                        ValType.F64,
                                        MutabilityType.Const)))
                .addMemory(
                        new ImportMemory(
                                "spectest",
                                "memory",
                                NativeMachineFactory.createMemory(new MemoryLimits(1, 2))))
                .addMemory(
                        new ImportMemory(
                                "spectest",
                                "shared_memory",
                                NativeMachineFactory.createMemory(new MemoryLimits(1, 2, true))))
                .addTable(
                        new ImportTable(
                                "spectest",
                                "table",
                                NativeMachineFactory.createImportTable(
                                        new Table(ValType.FuncRef, new TableLimits(10, 20)),
                                        REF_NULL_VALUE)))
                .build();
    }
}
