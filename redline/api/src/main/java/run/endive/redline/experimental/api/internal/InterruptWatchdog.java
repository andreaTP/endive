package run.endive.redline.experimental.api.internal;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * Raises {@link CtxBuffer#INTERRUPT_FLAG} for calls whose thread is interrupted, from a single
 * daemon poller shared by all machines.
 */
public final class InterruptWatchdog {

    private static final long POLL_INTERVAL_NANOS = 1_000_000L;

    private static final Set<Registration> ACTIVE = ConcurrentHashMap.newKeySet();

    private static final AtomicReference<Thread> POLLER = new AtomicReference<>();

    // true while the poller is parked with nothing to watch
    private static volatile boolean idle;

    private InterruptWatchdog() {}

    /** Raises the interrupt flag in a machine's context buffer. */
    @FunctionalInterface
    public interface InterruptSink {
        void requestInterrupt();
    }

    /** Watches {@code caller} until the returned handle is passed to {@link #exit}. */
    public static Registration enter(Thread caller, InterruptSink sink) {
        var poller = poller();
        var registration = new Registration(caller, sink);
        ACTIVE.add(registration);
        if (idle) {
            LockSupport.unpark(poller);
        }
        return registration;
    }

    /** Stops watching; once this returns the poller can no longer raise the flag for it. */
    public static void exit(Registration registration) {
        registration.deactivate();
        ACTIVE.remove(registration);
    }

    /** Visible for testing. */
    public static int activeCount() {
        return ACTIVE.size();
    }

    private static Thread poller() {
        Thread existing = POLLER.get();
        if (existing != null) {
            return existing;
        }
        synchronized (InterruptWatchdog.class) {
            existing = POLLER.get();
            if (existing != null) {
                return existing;
            }
            // no thread locals or class loader from whichever caller starts it
            var thread =
                    new Thread(
                            null,
                            InterruptWatchdog::pollLoop,
                            "endive-redline-interrupt",
                            0,
                            false);
            thread.setDaemon(true);
            thread.setContextClassLoader(null);
            thread.start();
            POLLER.set(thread);
            return thread;
        }
    }

    private static void pollLoop() {
        while (true) {
            // an interrupt status would make every park return at once
            Thread.interrupted();
            if (ACTIVE.isEmpty()) {
                idle = true;
                // re-check, enter() may have read idle before it was set
                if (ACTIVE.isEmpty()) {
                    LockSupport.park();
                }
                idle = false;
                continue;
            }
            for (Iterator<Registration> it = ACTIVE.iterator(); it.hasNext(); ) {
                try {
                    it.next().poll();
                } catch (RuntimeException e) {
                    // a failing sink must not stop the poller for everyone else
                    it.remove();
                }
            }
            LockSupport.parkNanos(POLL_INTERVAL_NANOS);
        }
    }

    /** One in-flight call. */
    public static final class Registration {

        private final Thread caller;
        private final InterruptSink sink;
        private boolean active = true;

        private Registration(Thread caller, InterruptSink sink) {
            this.caller = caller;
            this.sink = sink;
        }

        // synchronized with deactivate(), so the flag is never raised after exit()
        private void poll() {
            synchronized (this) {
                if (active && caller.isInterrupted()) {
                    sink.requestInterrupt();
                }
            }
        }

        private void deactivate() {
            synchronized (this) {
                active = false;
            }
        }
    }
}
