package run.endive.redline.experimental.api.internal;

import java.util.concurrent.atomic.AtomicLong;

/** Native bytes the garbage collector can't see, and when to ask it for a collection. */
public final class NativeFootprint {

    /** Charged per mapping, so collections also bound how many there are. */
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
