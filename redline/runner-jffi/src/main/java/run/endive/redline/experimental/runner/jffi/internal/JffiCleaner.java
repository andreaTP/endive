package run.endive.redline.experimental.runner.jffi.internal;

import java.lang.ref.Cleaner;

/** Frees jffi allocations once their owner is unreachable; a free must not reference it. */
final class JffiCleaner {

    private static final Cleaner CLEANER = Cleaner.create();

    private JffiCleaner() {}

    /** Runs {@code frees} in order once {@code owner} is unreachable. */
    static void register(Object owner, Runnable... frees) {
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
        }
    }
}
