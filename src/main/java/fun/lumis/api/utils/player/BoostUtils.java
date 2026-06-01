package fun.lumis.api.utils.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class BoostUtils {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final float BASE_HORIZONTAL = 1.61f;
    private static final float BASE_VERTICAL = 1.50f;

    private static final float[] YAW_TABLE = {
            1.61f, 1.61f, 1.61f, 1.61f, 1.61f, 1.61f, 1.62f, 1.62f, 1.62f, 1.63f,
            1.63f, 1.64f, 1.65f, 1.65f, 1.66f, 1.67f, 1.68f, 1.69f, 1.70f, 1.71f,
            1.72f, 1.73f, 1.73f, 1.75f, 1.76f, 1.78f, 1.79f, 1.81f, 1.83f, 1.85f,
            1.87f, 1.89f, 1.91f, 1.93f, 1.95f, 1.98f, 2.01f, 2.03f, 2.06f, 2.09f,
            2.12f, 2.16f, 2.19f, 2.23f, 2.27f, 2.31f, 2.35f, 2.31f, 2.27f, 2.23f,
            2.19f, 2.16f, 2.12f, 2.09f, 2.06f, 2.03f, 2.01f, 1.98f, 1.95f, 1.93f,
            1.89f, 1.87f, 1.85f, 1.83f, 1.81f, 1.79f, 1.78f, 1.76f, 1.75f, 1.73f,
            1.72f, 1.71f, 1.70f, 1.69f, 1.68f, 1.67f, 1.66f, 1.65f, 1.64f, 1.63f,
            1.63f, 1.63f, 1.62f, 1.62f, 1.62f, 1.61f, 1.61f, 1.61f, 1.61f, 1.61f,
            1.61f
    };

    private static final float[] PITCH_TABLE = {
            1.61f, 1.61f, 1.61f, 1.62f, 1.62f, 1.62f, 1.63f, 1.63f, 1.64f, 1.65f,
            1.65f, 1.66f, 1.67f, 1.68f, 1.69f, 1.70f, 1.71f, 1.72f, 1.73f, 1.73f,
            1.75f, 1.76f, 1.78f, 1.79f, 1.81f, 1.83f, 1.85f, 1.87f, 1.89f, 1.91f,
            1.93f, 1.95f, 1.98f, 2.01f, 2.03f, 2.06f, 2.09f, 2.12f, 2.16f, 2.19f,
            2.23f, 2.24f, 2.21f, 2.21f, 2.21f, 2.23f, 2.23f, 2.19f, 2.16f, 2.12f,
            2.09f, 2.06f, 2.03f, 2.01f, 1.98f, 1.95f, 1.93f, 1.89f, 1.87f, 1.85f,
            1.83f, 1.81f, 1.79f, 1.78f, 1.76f, 1.75f, 1.73f, 1.72f, 1.71f, 1.70f,
            1.69f, 1.68f, 1.67f, 1.66f, 1.65f, 1.64f, 1.63f, 1.63f, 1.63f, 1.62f,
            1.62f, 1.62f, 1.61f, 1.61f, 1.61f, 1.61f, 1.61f, 1.61f, 1.61f, 1.61f,
            1.61f
    };

    public static Vec3d getBoost(LivingEntity entity) {
        float speed = getRageSpeed(entity);

        Vec3d vec3d = entity.getRotationVector();
        Vec3d oldVelocity = Vec3d.fromPolar(entity.getPitch(), entity.getYaw()).multiply(speed);

        float f = entity.getPitch() * 0.017453292F;
        double d = Math.sqrt(vec3d.x * vec3d.x + vec3d.z * vec3d.z);
        double e = oldVelocity.horizontalLength();
        boolean bl = entity.getVelocity().y <= 0.0D;
        double g = bl && entity.hasStatusEffect(StatusEffects.SLOW_FALLING) ?
                Math.min(entity.getFinalGravity(), 0.01D) : entity.getFinalGravity();
        double h = MathHelper.square(Math.cos(f));

        oldVelocity = oldVelocity.add(0.0D, g * (-1.0D + h * 0.75D), 0.0D);

        double i;
        if (oldVelocity.y < 0.0D && d > 0.0D) {
            i = oldVelocity.y * -0.1D * h;
            oldVelocity = oldVelocity.add(vec3d.x * i / d, i, vec3d.z * i / d);
        }

        if (f < 0.0F && d > 0.0D) {
            i = e * (double) (-MathHelper.sin(f)) * 0.04D;
            oldVelocity = oldVelocity.add(-vec3d.x * i / d, i * 3.2D, -vec3d.z * i / d);
        }

        if (d > 0.0D) {
            oldVelocity = oldVelocity.add(
                    (vec3d.x / d * e - oldVelocity.x) * 0.1D,
                    0.0D,
                    (vec3d.z / d * e - oldVelocity.z) * 0.1D
            );
        }

        double length = oldVelocity.length();
        return new Vec3d(length, length, length).multiply(0.99D, 0.98D, 0.99D);
    }

    private static float getRageSpeed(LivingEntity entity) {
        float yawAbs = Math.abs(MathHelper.wrapDegrees(entity.getYaw()));
        float yawFolded = foldYaw(yawAbs);
        float pitchAbs = Math.abs(clampPitch(entity.getPitch()));

        if (pitchAbs >= 70f && pitchAbs <= 90f) {
            return 1.615f;
        }

        float yawSpeed = YAW_TABLE[Math.min((int) Math.ceil(yawFolded), 90)];
        int pitchIndex = Math.min((int) Math.ceil(pitchAbs), PITCH_TABLE.length - 1);
        float pitchSpeed = PITCH_TABLE[pitchIndex];

        float speed = pitchAbs >= 75f ? pitchSpeed : Math.max(yawSpeed, pitchSpeed);
        return Math.max(speed, pitchAbs >= 75f ? BASE_VERTICAL : BASE_HORIZONTAL);
    }

    private static float foldYaw(float yawAbs) {
        float folded180 = yawAbs > 180f ? 360f - yawAbs : yawAbs;
        return folded180 > 90f ? 180f - folded180 : folded180;
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90f, Math.min(90f, pitch));
    }


    public static Vec3d getBoostAntiTarget(LivingEntity entity, float speedSetting) {
        float yaw = Math.abs((entity.getYaw() - 360.0F) % 360.0F);
        float pitch = entity.getPitch();
        float absPitch = Math.abs(pitch);

        float baseSpeed = speedSetting;
        float pitchBonus = 0f;

        if (absPitch >= 30 && absPitch <= 50) pitchBonus = 0.15f;
        else if (absPitch >= 25 && absPitch <= 55) pitchBonus = 0.1f;
        else if (absPitch >= 20 && absPitch <= 60) pitchBonus = 0.05f;

        float speed = baseSpeed + pitchBonus;

        float[] centers = {45.0F, 135.0F, 225.0F, 315.0F};
        float minDiff = 9999.0F;
        for (float c : centers) {
            float diff = Math.abs(yaw - c);
            if (diff < minDiff) minDiff = diff;
        }

        if (minDiff < 15f) speed += 0.1f;
        else if (minDiff < 25f) speed += 0.05f;

        speed = Math.min(speed, 2.8f);
        return new Vec3d(speed, speed, speed);
    }

    public static Vec3d getBoostAntiTargetFast(LivingEntity entity) {
        float yaw = Math.abs((entity.getYaw() - 360.0F) % 360.0F);
        float pitch = entity.getPitch();
        float absPitch = Math.abs(pitch);

        float speedXZ = 2.5f;
        float speedY = 2.3f;

        if (absPitch >= 35 && absPitch <= 50) {
            speedXZ = 2.7f;
            speedY = 2.5f;
        } else if (absPitch >= 30 && absPitch <= 55) {
            speedXZ = 2.6f;
            speedY = 2.4f;
        }

        float[] centers = {45.0F, 135.0F, 225.0F, 315.0F};
        float minDiff = 9999.0F;
        for (float c : centers) {
            float diff = Math.abs(yaw - c);
            if (diff < minDiff) minDiff = diff;
        }

        if (minDiff < 20f) speedXZ += 0.15f;

        return new Vec3d(speedXZ, speedY, speedXZ);
    }

    public static Vec3d getBoostAntiTargetWithAura(LivingEntity entity, float auraRotatePitch, float auraRotateYaw, float speedSetting) {
        float absPitch = Math.abs(auraRotatePitch);
        float speedXZ = speedSetting;
        float speedY = speedSetting;

        if (absPitch >= 38 && absPitch <= 52) {
            speedXZ = Math.min(speedSetting + 0.2f, 2.7f);
            speedY = Math.min(speedSetting + 0.15f, 2.5f);
        } else if (absPitch >= 30 && absPitch <= 60) {
            speedXZ = Math.min(speedSetting + 0.1f, 2.6f);
            speedY = Math.min(speedSetting + 0.1f, 2.4f);
        } else if (absPitch >= 25 && absPitch <= 65) {
            speedY = speedSetting - 0.05f;
        } else {
            speedXZ = speedSetting - 0.1f;
            speedY = speedSetting - 0.15f;
        }

        return new Vec3d(speedXZ, speedY, speedXZ);
    }

    public static Vec3d getBoostslime(LivingEntity entity) {
        return getBoostCustom(entity, 2.1f * 20);
    }

    public static Vec3d getBoostbravo(LivingEntity entity) {
        return getBoostCustom(entity, 1.95f * 20);
    }

    public static Vec3d getBoostrw(LivingEntity entity) {
        return getBoostCustom(entity, 1.66f * 20);
    }

    public static Vec3d getBoostCustom(LivingEntity entity, float targetBps) {
        float maxSpeed = targetBps / 20.0F;
        float yaw = Math.abs((entity.getYaw() - 360.0F) % 360.0F);
        float pitch = entity.getPitch();
        float minSpeed = Math.min(maxSpeed * 0.7F, 1.67F);

        float[] centers = {45.0F, 135.0F, 225.0F, 315.0F};
        float minDiff = 9999.0F;
        for (float c : centers) {
            float diff = Math.abs(yaw - c);
            if (diff < minDiff) minDiff = diff;
        }

        float yawFactor = 1.0f - (minDiff / 45.0f);
        yawFactor = Math.max(0, Math.min(1, yawFactor));

        float pitchFactor = getPitchFactor(pitch);
        float combinedFactor = yawFactor * pitchFactor;
        float speed = minSpeed + (maxSpeed - minSpeed) * combinedFactor;

        // Физика (одинаковая)
        Vec3d vec3d = entity.getRotationVector();
        Vec3d oldVelocity = Vec3d.fromPolar(pitch, entity.getYaw()).multiply(speed);
        float f = pitch * 0.017453292F;
        double d = Math.sqrt(vec3d.x * vec3d.x + vec3d.z * vec3d.z);
        double e = oldVelocity.horizontalLength();
        boolean bl = entity.getVelocity().y <= 0.0D;
        double g = bl && entity.hasStatusEffect(StatusEffects.SLOW_FALLING) ? Math.min(entity.getFinalGravity(), 0.01D) : entity.getFinalGravity();
        double h = MathHelper.square(Math.cos(f));
        oldVelocity = oldVelocity.add(0.0D, g * (-1.0D + h * 0.75D), 0.0D);

        double i;
        if (oldVelocity.y < 0.0D && d > 0.0D) {
            i = oldVelocity.y * -0.1D * h;
            oldVelocity = oldVelocity.add(vec3d.x * i / d, i, vec3d.z * i / d);
        }
        if (f < 0.0F && d > 0.0D) {
            i = e * (double) (-MathHelper.sin(f)) * 0.04D;
            oldVelocity = oldVelocity.add(-vec3d.x * i / d, i * 3.2D, -vec3d.z * i / d);
        }
        if (d > 0.0D) {
            oldVelocity = oldVelocity.add((vec3d.x / d * e - oldVelocity.x) * 0.1D, 0.0D, (vec3d.z / d * e - oldVelocity.z) * 0.1D);
        }

        double length = oldVelocity.length();
        return new Vec3d(length, length, length).multiply(0.99D, 0.98D, 0.99D);
    }

    public static Vec3d getBoostFixedBps(LivingEntity entity, float targetBps) {
        float speed = targetBps / 20.0F;
        return new Vec3d(speed, speed, speed).multiply(0.99D, 0.98D, 0.99D);
    }

    private static float getPitchFactor(float pitch) {
        float absPitch = Math.abs(pitch);
        if (absPitch <= 5) return 1.0f;
        else if (absPitch <= 15) return 0.95f;
        else if (absPitch <= 25) return 0.85f;
        else if (absPitch <= 35) return 0.75f;
        else if (absPitch <= 45) return 0.65f;
        else if (absPitch <= 55) return 0.55f;
        else if (absPitch <= 65) return 0.45f;
        else if (absPitch <= 75) return 0.35f;
        else return 0.25f;
    }

    private BoostUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}