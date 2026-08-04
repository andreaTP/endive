package run.endive.redline.experimental.runner.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import run.endive.redline.experimental.api.NativeMachineFactoryProvider;
import run.endive.redline.experimental.runner.PanamaMachineFactoryProvider;

public class SpiDiscoveryTest {

    @Test
    public void discoversHighestPriorityProvider() {
        var provider = NativeMachineFactoryProvider.discover();
        assertTrue(provider.isPresent(), "Should discover at least one provider");
        assertEquals(100, provider.get().priority(), "Panama should win with priority 100");
        assertTrue(
                provider.get() instanceof PanamaMachineFactoryProvider,
                "Should be Panama on JDK 25+");
    }
}
