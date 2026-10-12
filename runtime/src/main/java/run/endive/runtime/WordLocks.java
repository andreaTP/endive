package run.endive.runtime;

// Striped locks for fallback atomics, keyed by 8-byte word so overlapping atomics share one
final class WordLocks {
    private static final int STRIPE_BITS = 8;

    private final Object[] locks = new Object[1 << STRIPE_BITS];

    WordLocks() {
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
    }

    Object forAddress(int address) {
        // Fibonacci hashing, so power-of-two strides don't pile onto one stripe
        return locks[((address >>> 3) * 0x9E3779B9) >>> (32 - STRIPE_BITS)];
    }
}
