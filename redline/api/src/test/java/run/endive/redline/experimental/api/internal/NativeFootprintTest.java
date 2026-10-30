package run.endive.redline.experimental.api.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class NativeFootprintTest {

    private final AtomicInteger collections = new AtomicInteger();
    private final NativeFootprint footprint =
            new NativeFootprint(100, collections::incrementAndGet);

    @Test
    public void collectsOnceGrowthPassesTheThreshold() {
        footprint.add(99);
        assertEquals(0, collections.get());
        footprint.add(1);
        assertEquals(1, collections.get());
        // the next request waits for another 100 bytes
        footprint.add(99);
        assertEquals(1, collections.get());
        footprint.add(1);
        assertEquals(2, collections.get());
    }

    @Test
    public void aLargeLiveSetMustDoubleBeforeTheNextCollection() {
        footprint.add(1000);
        assertEquals(1, collections.get());
        footprint.add(999);
        assertEquals(1, collections.get());
        footprint.add(1);
        assertEquals(2, collections.get());
    }

    @Test
    public void aHugeThresholdNeverCollects() {
        var huge = new NativeFootprint(Long.MAX_VALUE, collections::incrementAndGet);
        huge.add(1000);
        huge.remove(500);
        huge.add(1000);
        assertEquals(0, collections.get());
    }

    @Test
    public void freeingLowersTheThreshold() {
        footprint.add(1000);
        assertEquals(1, collections.get());
        footprint.remove(1000);
        footprint.add(100);
        assertEquals(2, collections.get());
    }
}
