package run.endive.redline.experimental.runner.jffi.internal;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.kenai.jffi.CallContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.ValType;

/** jffi never frees a call context, so instances must share theirs instead of making new ones. */
public class CallContextSharingTest {

    @Test
    public void oneSignatureGetsOneCallContext() {
        var first = FunctionType.of(List.of(ValType.I32, ValType.I64), List.of(ValType.F64));
        var second = FunctionType.of(List.of(ValType.I32, ValType.I64), List.of(ValType.F64));
        assertSame(context(first), context(second));
    }

    private static CallContext context(FunctionType type) {
        return JffiNativeMachine.createEntryTrampolineCallContext(type);
    }
}
