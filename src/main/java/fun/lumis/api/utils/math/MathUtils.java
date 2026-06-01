package fun.lumis.api.utils.math;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import fun.lumis.api.QClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;

public class MathUtils implements QClient {
    public static FastRandom fastRandomize = new FastRandom();

    public static double direction(float rotationYaw, final double moveForward, final double moveStrafing) {
        if (moveForward < 0F) rotationYaw += 180F;

        float forward = 1F;

        if (moveForward < 0F) forward = -0.5F;
        else if (moveForward > 0F) forward = 0.5F;

        if (moveStrafing > 0F) rotationYaw -= 90F * forward;
        if (moveStrafing < 0F) rotationYaw += 90F * forward;

        return Math.toRadians(rotationYaw);
    }

    public static float randomNew(double min, double max) {
        if (min > max) return (float) (fastRandomize.nextFloat() * (min - max) + max);
        return (float) (fastRandomize.nextFloat() * (max - min) + min);
    }

    public static double getBps(Entity player) {
        double dx = player.getX() - player.prevX;
        double dy = player.getY() - player.prevY;
        double dz = player.getZ() - player.prevZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return distance * 20.0F;
    }

    public static float calculateBPS() {
        if (mc.player == null) return 0;

        double dx = mc.player.getX() - mc.player.prevX;
        double dy = mc.player.getY() - mc.player.prevY;
        double dz = mc.player.getZ() - mc.player.prevZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        float timerSpeed = 1.0f;
        float bps = (float) (distance * timerSpeed * 20.0D);
        return (float) (Math.round(bps * 10) / 10.0f);
    }

    public static double getTargetCompensatedSpeed(Entity target) {
        final double baseSpeed = 1.5;

        if (target == null) {
            return baseSpeed;
        }

        double targetBps = calculateBPS();

        final double speedFactor = 0.00342;
        double bonusSpeed = targetBps * speedFactor;

        return baseSpeed + bonusSpeed;
    }

    public static float random(final float min, final float max) {
        SecureRandom secureRandom = new SecureRandom();
        double randA = secureRandom.nextDouble();
        double randB = secureRandom.nextDouble();
        double randC = secureRandom.nextGaussian() * 0.02f;
        double smoothFactor = Math.pow(randA, 1.0 + secureRandom.nextDouble() * 0.7);
        double mixFactor = (randB * 0.8 + 0.1) * (Math.log1p(randA * 3) * 0.5 + 0.5);
        return (float) (min + (max - min) * smoothFactor * mixFactor + randC);
    }

    public static double randomBest(double min, double max) {
        return ThreadLocalRandom.current().nextDouble() * (max - min) + min;
    }

    public static boolean isHovered(double x, double y, double width, double height, double mouseX, double mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }


    public static float interpolate(float prev, float to, float value) {
        return prev + (to - prev) * value;
    }
    public static Vec3d interpolate(Vec3d end, Vec3d start, float multiple) {
        return new Vec3d(interpolate(end.getX(), start.getX(), multiple), interpolate(end.getY(), start.getY(), multiple), interpolate(end.getZ(), start.getZ(), multiple));
    }
    public static Vec3d interpolate(Entity entity, float partialTicks) {
        double posX = MathHelper.lerp(partialTicks, entity.prevX, entity.getX());
        double posY = MathHelper.lerp(partialTicks, entity.prevY, entity.getY());
        double posZ = MathHelper.lerp(partialTicks, entity.prevZ, entity.getZ());
        return new Vec3d(posX, posY, posZ);
    }

    public static double interpolate(double current, double old, double scale) {
        return old + (current - old) * scale;
    }

    public static float round(float number) {
        return Math.round(number * 10f) / 10f;
    }

    public static double round(double num, double increment) {
        double v = (double) Math.round(num / increment) * increment;
        BigDecimal bd = new BigDecimal(v);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
    public static float lerp(float current, float old, float scale) {
        return current + (old - current) * clamp(scale, 0, 1);
    }
    public static float clamp(float value,float min, float max) {
        if (value <= min) {
            return min;
        }
        return Math.min(value, max);
    }
    public static double clamp(double min, double max, double n) {
        return Math.max(min, Math.min(max, n));
    }
    public static <T extends Number> T ler1p(T input, T target, double step) {
        double start = input.doubleValue();
        double end = target.doubleValue();
        double result = start + step * (end - start);

        if (input instanceof Integer) {
            return (T) Integer.valueOf((int) Math.round(result));
        } else if (input instanceof Double) {
            return (T) Double.valueOf(result);
        } else if (input instanceof Float) {
            return (T) Float.valueOf((float) result);
        } else if (input instanceof Long) {
            return (T) Long.valueOf(Math.round(result));
        } else if (input instanceof Short) {
            return (T) Short.valueOf((short) Math.round(result));
        } else if (input instanceof Byte) {
            return (T) Byte.valueOf((byte) Math.round(result));
        } else {
            throw new IllegalArgumentException("Unsupported type: " + input.getClass().getSimpleName());
        }
    }
}
