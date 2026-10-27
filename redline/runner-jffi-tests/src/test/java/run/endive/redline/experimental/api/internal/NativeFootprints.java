package run.endive.redline.experimental.api.internal;

/** Gives this module's tests the native footprint, which main code keeps package-private. */
public final class NativeFootprints {

    private NativeFootprints() {}

    public static long live() {
        return NativeFootprint.live();
    }
}
