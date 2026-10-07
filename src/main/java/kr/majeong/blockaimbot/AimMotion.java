package kr.majeong.blockaimbot;

import java.util.Random;

/** Stateful, smooth variation of angular motion; no Minecraft or input dependencies. */
public final class AimMotion {
    private final Random random;
    private boolean active;
    private int age, noiseAge, noiseDuration;
    private double initialError, perpendicularYaw, perpendicularPitch, arc;
    private double fromSpeed, toSpeed, fromYaw, toYaw, fromPitch, toPitch;

    public AimMotion() { this(new Random()); }
    public AimMotion(Random random) { this.random = random; }

    public void reset() {
        active = false;
        age = noiseAge = 0;
    }

    public float[] step(float yaw, float pitch, double targetYaw, double targetPitch,
                        double degreesPerSecond, double randomizationPercent) {
        double dy = AimMath.wrap(targetYaw - yaw);
        double dp = targetPitch - pitch;
        double error = Math.hypot(dy, dp);
        double strength = Math.max(0, Math.min(0.5, randomizationPercent / 100));
        double baseStep = Math.max(0, degreesPerSecond) / 20;
        if (strength == 0) {
            reset();
            return AimMath.step(yaw, pitch, targetYaw, targetPitch, baseStep);
        }
        // Finish at the true target so holding the trigger never creates idle shaking.
        if (error <= 0.05) return AimMath.step(yaw, pitch, targetYaw, targetPitch, baseStep);
        if (!active) begin(dy, dp, error);

        double t = (double) noiseAge / noiseDuration;
        double blend = t * t * (3 - 2 * t);
        double speedNoise = lerp(fromSpeed, toSpeed, blend);
        double yawNoise = lerp(fromYaw, toYaw, blend);
        double pitchNoise = lerp(fromPitch, toPitch, blend);
        advanceNoise();
        age++;

        double progress = Math.max(0, Math.min(1, 1 - error / initialError));
        double taper = Math.min(1, error / 3);
        double curve = arc * strength * Math.sin(Math.PI * progress) * taper;
        double offsetYaw = perpendicularYaw * curve + yawNoise * 4 * strength * taper;
        double offsetPitch = perpendicularPitch * curve + pitchNoise * 4 * strength * taper;
        // Keep the drifting guide close enough to the true target to guarantee progress.
        double offsetLength = Math.hypot(offsetYaw, offsetPitch);
        double limit = error * 0.2;
        if (offsetLength > limit) {
            offsetYaw *= limit / offsetLength;
            offsetPitch *= limit / offsetLength;
        }

        double startEase = 0.35 + 0.65 * Math.min(1, age / 6.0);
        double approachEase = 0.35 + 0.65 * Math.min(1, error / 8);
        double speed = baseStep * (1 + strength * speedNoise) * startEase * approachEase;
        // Small corrections slow down instead of snapping past the true target.
        return AimMath.step(yaw, pitch, targetYaw + offsetYaw,
                Math.max(-90, Math.min(90, targetPitch + offsetPitch)), Math.min(speed, error * 0.65));
    }

    private void begin(double dy, double dp, double error) {
        active = true;
        initialError = error;
        perpendicularYaw = -dp / error;
        perpendicularPitch = dy / error;
        // The configured strength is applied to this arc in step().
        arc = (random.nextDouble() * 2 - 1) * Math.min(6, error * 0.1);
        fromSpeed = randomSigned();
        fromYaw = randomSigned();
        fromPitch = randomSigned();
        nextNoiseSegment();
    }

    private void advanceNoise() {
        if (++noiseAge >= noiseDuration) {
            fromSpeed = toSpeed;
            fromYaw = toYaw;
            fromPitch = toPitch;
            nextNoiseSegment();
        }
    }

    private void nextNoiseSegment() {
        noiseAge = 0;
        noiseDuration = 6 + random.nextInt(7); // 0.3–0.6 seconds at 20 client ticks/sec.
        toSpeed = randomSigned();
        toYaw = randomSigned();
        toPitch = randomSigned();
    }

    private double randomSigned() { return random.nextDouble() * 2 - 1; }
    private static double lerp(double from, double to, double t) { return from + (to - from) * t; }
}
