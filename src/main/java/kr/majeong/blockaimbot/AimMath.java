package kr.majeong.blockaimbot;

public final class AimMath {
    private AimMath() {}

    public static double wrap(double angle) {
        double value = angle % 360;
        if (value >= 180) value -= 360;
        if (value < -180) value += 360;
        return value;
    }

    /** Limit the total angular travel, not each axis separately. */
    public static float[] step(float yaw, float pitch, double targetYaw, double targetPitch, double maxStep) {
        double dy = wrap(targetYaw - yaw);
        double dp = targetPitch - pitch;
        double length = Math.hypot(dy, dp);
        double factor = length > 0 ? Math.min(1, Math.max(0, maxStep) / length) : 0;
        return new float[] {(float) wrap(yaw + dy * factor),
                (float) Math.max(-90, Math.min(90, pitch + dp * factor))};
    }
}
