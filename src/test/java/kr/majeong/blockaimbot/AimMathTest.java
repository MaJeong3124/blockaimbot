package kr.majeong.blockaimbot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AimMathTest {
    @Test void crossesYawBoundaryByShortestPath() {
        float[] a = AimMath.step(179, 0, -179, 0, 1);
        assertEquals(-180, a[0], 0.0001);
    }
    @Test void neverOvershootsOrExceedsCombinedSpeed() {
        float[] a = AimMath.step(0, 0, 30, 40, 5);
        assertEquals(5, Math.hypot(a[0], a[1]), 0.0001);
        a = AimMath.step(0, 0, 3, 4, 100);
        assertEquals(3, a[0]); assertEquals(4, a[1]);
    }
    @Test void zeroSpeedAndAlignedTargetStayStill() {
        assertArrayEquals(new float[]{12, 13}, AimMath.step(12, 13, 50, 60, 0));
        assertArrayEquals(new float[]{12, 13}, AimMath.step(12, 13, 12, 13, 10));
    }
}
