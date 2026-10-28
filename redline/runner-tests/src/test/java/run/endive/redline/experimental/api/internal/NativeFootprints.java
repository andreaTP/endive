package run.endive.redline.experimental.api.internal;

/** Test access to the package-private native footprint. */
public final class NativeFootprints {

    private NativeFootprints() {}

    public static long live() {
        return NativeFootprint.live();
    }
}
