package run.endive.redline.experimental.runner.jffi.internal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import run.endive.redline.experimental.api.NativeMachineFactoryProvider;

public class SpiDiscoveryTest {

    @Test
    public void discoversJffiProvider() {
        var provider = NativeMachineFactoryProvider.discover();
        assertTrue(provider.isPresent(), "Should discover JFFI provider");
        assertTrue(provider.get().priority() > 0, "JFFI should have positive priority");
    }
}
