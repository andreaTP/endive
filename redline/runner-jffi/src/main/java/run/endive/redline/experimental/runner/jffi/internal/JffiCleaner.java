package run.endive.redline.experimental.runner.jffi.internal;

import java.lang.ref.Cleaner;
import run.endive.redline.experimental.api.internal.NativeResourceCount;

/**
 * Frees jffi allocations once the garbage collector finds their owner unreachable, as Panama
 * does for automatic arenas. A free must not reach its owner, or the owner would never
 * become unreachable: capture addresses and handles only.
 */
final class JffiCleaner {

    private static final Cleaner CLEANER = Cleaner.create();

    private JffiCleaner() {}

    /** Runs {@code frees} in order once {@code owner} is unreachable, counted as one resource. */
    static void register(Object owner, Runnable... frees) {
        NativeResourceCount.allocated();
        CLEANER.register(owner, new Frees(frees));
    }

    private static final class Frees implements Runnable {
        private final Runnable[] frees;

        Frees(Runnable[] frees) {
            this.frees = frees;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public void run() {
            for (var free : frees) {
                try {
                    free.run();
                } catch (Throwable e) {
                    // a cleanup action has nowhere to report a failure: free the rest
                }
            }
            NativeResourceCount.freed();
        }
    }
}
