package run.endive.redline.experimental.api.internal;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Native bytes Redline holds that the garbage collector doesn't see: compiled code, linear
 * memory and tables. Since unreachable instances free them only once a collection runs, it asks
 * for one when they have grown by as much as they were at their last low point, and by at least
 * {@code endive.redline.gcThreshold} bytes, as the JDK does for direct buffers.
 */
public final class NativeFootprint {

    /** Charged for each mapping on top of its bytes, so collections also bound mapping counts. */
    public static final long MAPPING_COST = 64 << 10;

    private static final NativeFootprint GLOBAL =
            new NativeFootprint(Long.getLong("endive.redline.gcThreshold", 256L << 20), System::gc);

    private final long minGrowth;
    private final Runnable collect;
    private final AtomicLong live = new AtomicLong();
    private final AtomicLong collectAt;

    NativeFootprint(long minGrowth, Runnable collect) {
        this.minGrowth = minGrowth;
        this.collect = collect;
        this.collectAt = new AtomicLong(minGrowth);
    }

    public static void allocated(long bytes) {
        GLOBAL.add(bytes);
    }

    public static void freed(long bytes) {
        GLOBAL.remove(bytes);
    }

    /** Bytes allocated and not yet freed. */
    static long live() {
        return GLOBAL.live.get();
    }

    void add(long bytes) {
        long now = live.addAndGet(bytes);
        long at = collectAt.get();
        // one thread asks; the others carry on
        if (now >= at && collectAt.compareAndSet(at, threshold(now))) {
            collect.run();
        }
    }

    void remove(long bytes) {
        collectAt.accumulateAndGet(threshold(live.addAndGet(-bytes)), Math::min);
    }

    private long threshold(long now) {
        long growth = Math.max(minGrowth, now);
        return now > Long.MAX_VALUE - growth ? Long.MAX_VALUE : now + growth;
    }
}
