package run.endive.redline.experimental.api.internal;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class InterruptWatchdogTest {

    @AfterEach
    public void clearInterruptStatus() {
        Thread.interrupted();
    }

    @Test
    public void raisesTheFlagOfAnInterruptedCallerUntilExit() {
        var flag = new Flag();
        var registration = InterruptWatchdog.enter(flag);
        try {
            Thread.currentThread().interrupt();
            awaitTrue(() -> flag.raised);
        } finally {
            InterruptWatchdog.exit(registration);
        }

        flag.raised = false;
        // the caller is still interrupted, but no longer watched
        long end = System.nanoTime() + 3 * InterruptWatchdog.POLL_INTERVAL_NANOS;
        while (System.nanoTime() < end) {
            LockSupport.parkNanos(MILLISECONDS.toNanos(1));
        }
        assertFalse(flag.raised);
    }

    @Test
    public void aFailingSinkIsDroppedAndLogged() {
        var failure = new IllegalStateException("closed");
        InterruptWatchdog.InterruptSink broken =
                () -> {
                    throw failure;
                };
        var logged = new AtomicReference<Throwable>();
        var log = Logger.getLogger(InterruptWatchdog.class.getName());
        var handler =
                new Handler() {
                    @Override
                    public void publish(LogRecord record) {
                        logged.compareAndSet(null, record.getThrown());
                    }

                    @Override
                    public void flush() {}

                    @Override
                    public void close() {}
                };
        log.addHandler(handler);
        log.setUseParentHandlers(false);
        int before = InterruptWatchdog.activeCount();
        var brokenRegistration = InterruptWatchdog.enter(broken);
        var flag = new Flag();
        var registration = InterruptWatchdog.enter(flag);
        try {
            Thread.currentThread().interrupt();
            awaitTrue(() -> flag.raised && logged.get() != null);
            assertSame(failure, logged.get());
            assertEquals(before + 1, InterruptWatchdog.activeCount(), "broken call dropped");
        } finally {
            InterruptWatchdog.exit(registration);
            InterruptWatchdog.exit(brokenRegistration);
            log.removeHandler(handler);
            log.setUseParentHandlers(true);
        }
    }

    @Test
    public void aCallWakesAnIdlePoller() {
        var flag = new Flag();
        InterruptWatchdog.exit(InterruptWatchdog.enter(flag));
        awaitTrue(InterruptWatchdog::pollerIdle);

        var registration = InterruptWatchdog.enter(flag);
        try {
            long start = System.nanoTime();
            Thread.currentThread().interrupt();
            awaitTrue(() -> flag.raised);
            // well before the idle poller would wake up on its own
            assertTrue(System.nanoTime() - start < MILLISECONDS.toNanos(500));
        } finally {
            InterruptWatchdog.exit(registration);
        }
    }

    @Test
    public void thePollerExitsWhenIdleAndRestartsOnTheNextCall() {
        long idleExit = InterruptWatchdog.IDLE_EXIT_NANOS.getAndSet(MILLISECONDS.toNanos(100));
        try {
            var flag = new Flag();
            var first = new AtomicReference<Thread>();
            var registration = InterruptWatchdog.enter(flag);
            try {
                awaitTrue(() -> first.updateAndGet(t -> pollerThread()) != null);
            } finally {
                InterruptWatchdog.exit(registration);
            }

            awaitTrue(() -> !InterruptWatchdog.pollerRunning() && !first.get().isAlive());

            registration = InterruptWatchdog.enter(flag);
            try {
                // the old poller is dead, so this one was started by the call above
                assertNotNull(pollerThread());
                Thread.currentThread().interrupt();
                awaitTrue(() -> flag.raised);
            } finally {
                InterruptWatchdog.exit(registration);
            }
        } finally {
            InterruptWatchdog.IDLE_EXIT_NANOS.set(idleExit);
        }
    }

    private static Thread pollerThread() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().equals("endive-redline-interrupt") && t.isAlive())
                .findFirst()
                .orElse(null);
    }

    // parkNanos, as the caller is often interrupted on purpose and cannot sleep
    private static void awaitTrue(BooleanSupplier condition) {
        long deadline = System.nanoTime() + SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "timed out");
            LockSupport.parkNanos(MILLISECONDS.toNanos(1));
        }
    }

    private static final class Flag implements InterruptWatchdog.InterruptSink {
        volatile boolean raised;

        @Override
        public void requestInterrupt() {
            raised = true;
        }
    }
}
