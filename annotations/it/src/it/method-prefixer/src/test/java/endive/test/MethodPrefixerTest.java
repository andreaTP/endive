package endive.test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import run.endive.runtime.Instance;
import run.endive.runtime.Machine;
import run.endive.wasm.UninstantiableException;
import run.endive.wasm.WasmModule;

/**
 * The module traps in its start function, so instantiating it throws from inside the compiled code
 * and the stack trace carries the generated method names. The module names its three functions
 * "trap", "innerFunc" and "start".
 */
public class MethodPrefixerTest {

    @Test
    public void nameSectionPrefixerNamesTheGeneratedMethods() {
        var methods = methodNamesFromTrap(NamedTrapModule.load(), NamedTrapModule::create);

        assertTrue(methods.contains("trap_0"), "Expected trap_0 in: " + methods);
        assertTrue(methods.contains("innerFunc_1"), "Expected innerFunc_1 in: " + methods);
        assertTrue(methods.contains("start_2"), "Expected start_2 in: " + methods);
    }

    @Test
    public void withoutAPrefixerMethodsKeepTheDefaultNames() {
        var methods = methodNamesFromTrap(DefaultTrapModule.load(), DefaultTrapModule::create);

        assertTrue(methods.contains("func_0"), "Expected func_0 in: " + methods);
        assertTrue(methods.contains("func_1"), "Expected func_1 in: " + methods);
        assertTrue(methods.contains("func_2"), "Expected func_2 in: " + methods);
    }

    /** Instantiates the module and returns every method name on the resulting stack traces. */
    private static List<String> methodNamesFromTrap(
            WasmModule module, Function<Instance, Machine> machineFactory) {
        Throwable thrown =
                assertThrows(
                        UninstantiableException.class,
                        () -> Instance.builder(module).withMachineFactory(machineFactory).build());

        var methods = new ArrayList<String>();
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            for (var frame : t.getStackTrace()) {
                methods.add(frame.getMethodName());
            }
        }
        return methods;
    }
}
