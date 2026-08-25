package run.endive.redline.experimental.api.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class RedlineTargetTest {

    private static final String OS_NAME = "endive.redline.os.name";
    private static final String OS_ARCH = "endive.redline.os.arch";

    @AfterEach
    public void clearOverrides() {
        System.clearProperty(OS_NAME);
        System.clearProperty(OS_ARCH);
    }

    private static Optional<RedlineTarget> detect(String osName, String arch) {
        System.setProperty(OS_NAME, osName);
        System.setProperty(OS_ARCH, arch);
        return RedlineTarget.detectHost();
    }

    @Test
    public void detectsSupportedPlatforms() {
        assertEquals(Optional.of(RedlineTarget.LINUX_X86_64), detect("Linux", "amd64"));
        assertEquals(Optional.of(RedlineTarget.LINUX_X86_64), detect("Linux", "x86_64"));
        assertEquals(Optional.of(RedlineTarget.LINUX_AARCH64), detect("Linux", "aarch64"));
        assertEquals(Optional.of(RedlineTarget.MACOS_AARCH64), detect("Mac OS X", "arm64"));
        assertEquals(Optional.of(RedlineTarget.MACOS_X86_64), detect("Mac OS X", "x86_64"));
        assertEquals(Optional.of(RedlineTarget.WINDOWS_X86_64), detect("Windows 11", "amd64"));
        assertEquals(Optional.of(RedlineTarget.WINDOWS_AARCH64), detect("Windows 11", "aarch64"));
    }

    /**
     * An unrecognised architecture must not be reported as x86_64. Callers use the
     * result to select a native code blob, so guessing wrong hands machine code for
     * the wrong ISA to the CPU and crashes the JVM, instead of falling back to the
     * build-time compiler.
     */
    @Test
    public void unknownArchitectureIsNotMistakenForX8664() {
        for (String arch : new String[] {"riscv64", "ppc64le", "s390x", "arm", "mips64", ""}) {
            assertTrue(
                    detect("Linux", arch).isEmpty(),
                    "Linux/" + arch + " must not resolve to an x86_64 target");
        }
    }

    @Test
    public void unknownOperatingSystemIsUnsupported() {
        assertTrue(detect("FreeBSD", "amd64").isEmpty());
        assertTrue(detect("SunOS", "amd64").isEmpty());
    }

    @Test
    public void everyTargetRoundTripsThroughItsTriple() {
        for (RedlineTarget target : RedlineTarget.values()) {
            assertEquals(Optional.of(target), RedlineTarget.fromTriple(target.triple()));
        }
        assertTrue(RedlineTarget.fromTriple("not-a-real-triple").isEmpty());
    }
}
