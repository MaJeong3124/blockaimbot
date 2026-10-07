package kr.majeong.blockaimbot;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AimMotionTest {
    @Test void zeroRandomizationPreservesStraightMotion() {
        AimMotion motion = new AimMotion(new Random(1));
        assertArrayEquals(AimMath.step(179, 10, -120, -20, 9),
                motion.step(179, 10, -120, -20, 180, 0));
    }

    @Test void pathCurvesAndSpeedChangesDuringOneAcquisition() {
        AimMotion motion = new AimMotion(new Random(42));
        float yaw = 0, pitch = 0;
        double minSpeed = Double.MAX_VALUE, maxSpeed = 0, maxDeviation = 0;
        for (int i = 0; i < 20; i++) {
            float[] a = motion.step(yaw, pitch, 120, 0, 180, 15);
            double travel = Math.hypot(AimMath.wrap(a[0] - yaw), a[1] - pitch);
            minSpeed = Math.min(minSpeed, travel);
            maxSpeed = Math.max(maxSpeed, travel);
            maxDeviation = Math.max(maxDeviation, Math.abs(a[1]));
            yaw = a[0]; pitch = a[1];
        }
        assertTrue(maxDeviation > 0.01, "horizontal target must have a curved path");
        assertTrue(maxSpeed - minSpeed > 0.1, "speed should vary while approaching");
    }

    @Test void convergesWithinSpeedLimitWithoutIdleShakeAcrossSeamsAndPoles() {
        for (int seed = 0; seed < 30; seed++) {
            checkPath(seed, 179, 20, -120, -45);
            checkPath(seed, -175, 88, 120, 89);
            checkPath(seed, 10, -88, -130, -89);
        }
    }

    private void checkPath(int seed, float yaw, float pitch, double targetYaw, double targetPitch) {
        AimMotion motion = new AimMotion(new Random(seed));
        double oldError = error(yaw, pitch, targetYaw, targetPitch);
        for (int i = 0; i < 300; i++) {
            float[] a = motion.step(yaw, pitch, targetYaw, targetPitch, 180, 50);
            assertTrue(Float.isFinite(a[0]) && Float.isFinite(a[1]));
            assertTrue(a[1] >= -90 && a[1] <= 90);
            assertTrue(Math.hypot(AimMath.wrap(a[0] - yaw), a[1] - pitch) <= 13.501);
            double newError = error(a[0], a[1], targetYaw, targetPitch);
            assertTrue(newError <= oldError + 0.0001, "must continue towards the true target");
            yaw = a[0]; pitch = a[1]; oldError = newError;
        }
        assertEquals(0, oldError, 0.0001);
        assertArrayEquals(new float[]{yaw, pitch}, motion.step(yaw, pitch, targetYaw, targetPitch, 180, 50));
    }

    @Test void resetRestartsEasingForTheNewTarget() {
        AimMotion motion = new AimMotion(new Random(7));
        for (int i = 0; i < 10; i++) motion.step(0, 0, 120, 0, 180, 50);
        motion.reset();
        float[] a = motion.step(0, 0, -120, 0, 180, 50);
        assertTrue(a[0] < 0);
        assertTrue(Math.hypot(a[0], a[1]) <= 6.189, "new acquisition must restart slow easing");
    }

    private static double error(float yaw, float pitch, double targetYaw, double targetPitch) {
        return Math.hypot(AimMath.wrap(targetYaw - yaw), targetPitch - pitch);
    }
}
