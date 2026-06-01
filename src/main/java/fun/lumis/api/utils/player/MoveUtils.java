package fun.lumis.api.utils.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Objects;

import static fun.lumis.api.utils.input.MovingUtil.calculateDirection;

public class MoveUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void setMotion(final double motion) {
        if (mc.player == null) return;

        double forward = mc.player.input.movementForward;
        double strafe = mc.player.input.movementSideways;
        float yaw = mc.player.getYaw();

        if (forward == 0 && strafe == 0) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
        } else {
            if (forward != 0) {
                if (strafe > 0) {
                    yaw += (float) (forward > 0 ? -45 : 45);
                } else if (strafe < 0) {
                    yaw += (float) (forward > 0 ? 45 : -45);
                }
                strafe = 0;
                if (forward > 0) {
                    forward = 1;
                } else if (forward < 0) {
                    forward = -1;
                }
            }

            double motionX = forward * motion * MathHelper.cos((float) Math.toRadians(yaw + 90.0f))
                    + strafe * motion * MathHelper.sin((float) Math.toRadians(yaw + 90.0f));
            double motionZ = forward * motion * MathHelper.sin((float) Math.toRadians(yaw + 90.0f))
                    - strafe * motion * MathHelper.cos((float) Math.toRadians(yaw + 90.0f));

            mc.player.setVelocity(motionX, mc.player.getVelocity().y, motionZ);
        }
    }

    public static double getSpeed() {
        if (mc.player == null) return 0;
        Vec3d velocity = mc.player.getVelocity();
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    public static void setVelocity(double velocity) {
        double[] direction = calculateDirection(velocity);
        ((ClientPlayerEntity) Objects.requireNonNull(mc.player)).setVelocity(direction[0], mc.player.getVelocity().getY(), direction[1]);
    }

    public static void setVelocity(double velocity, double y) {
        double[] direction = calculateDirection(velocity);
        ((ClientPlayerEntity)Objects.requireNonNull(mc.player)).setVelocity(direction[0], y, direction[1]);
    }

    public static void strafe() {
        strafe(getSpeed());
    }

    public static void strafe(double speed) {
        if (mc.player == null) return;

        float yaw = mc.player.getYaw();
        double forward = mc.player.input.movementForward;
        double strafe = mc.player.input.movementSideways;

        if (forward == 0 && strafe == 0) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
            return;
        }

        if (forward != 0) {
            if (strafe > 0) {
                yaw += forward > 0 ? -45 : 45;
            } else if (strafe < 0) {
                yaw += forward > 0 ? 45 : -45;
            }
            strafe = 0;
            forward = forward > 0 ? 1 : -1;
        }

        double rad = Math.toRadians(yaw + 90);
        double motionX = forward * speed * Math.cos(rad) + strafe * speed * Math.sin(rad);
        double motionZ = forward * speed * Math.sin(rad) - strafe * speed * Math.cos(rad);

        mc.player.setVelocity(motionX, mc.player.getVelocity().y, motionZ);
    }

    public static boolean isMoving() {
        if (mc.player == null) return false;
        return mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0;
    }
}