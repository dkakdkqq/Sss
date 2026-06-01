package fun.lumis.api.utils.combat;

import lombok.Getter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import fun.lumis.api.QClient;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;


import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PredictUtils implements QClient {

    private static final Map<UUID, PositionData> positionCache = new ConcurrentHashMap<>();

    @Getter
    public static class PositionData {
        private double serverX, serverY, serverZ;
        private double prevServerX, prevServerY, prevServerZ;
        private double backUpX, backUpY, backUpZ;
        private double lastSpeed, prevSpeed;
        private long lastUpdate;

        public Vec3d getResolvedPos() {
            return new Vec3d(serverX, serverY, serverZ);
        }

        public Vec3d getResolvedForward() {
            return new Vec3d(
                    serverX - prevServerX,
                    serverY - prevServerY,
                    serverZ - prevServerZ
            );
        }

        public void update(double x, double y, double z) {
            backUpX = prevServerX;
            backUpY = prevServerY;
            backUpZ = prevServerZ;

            prevServerX = serverX;
            prevServerY = serverY;
            prevServerZ = serverZ;
            serverX = x;
            serverY = y;
            serverZ = z;

            prevSpeed = lastSpeed;
            lastSpeed = getResolvedForward().length() * 20;
            lastUpdate = System.currentTimeMillis();
        }

        public boolean isSpeedChanged() {
            return lastSpeed >= 20 || (lastSpeed != prevSpeed && lastSpeed == 0);
        }
    }

    public static void updateEntity(LivingEntity entity) {
        PositionData data = positionCache.computeIfAbsent(entity.getUuid(), k -> new PositionData());
        data.update(entity.getX(), entity.getY(), entity.getZ());
    }

    public static PositionData getData(LivingEntity entity) {
        return positionCache.get(entity.getUuid());
    }

    public static Vec3d predict(LivingEntity entity, int ticks, float extraForward, boolean isMeFlying) {
        PositionData data = getData(entity);
        Vec3d pos = new Vec3d(entity.getX(), entity.getY() + entity.getStandingEyeHeight() / 2, entity.getZ());

        if (data == null) {
            return predictElytraPhysics(entity, pos, ticks);
        }

        Vec3d forward = data.getResolvedForward();
        double speed = data.getLastSpeed();
        boolean isHighSpeed = data.isSpeedChanged();

        if (entity.isGliding()) {
            double horizontalSpeed = Math.hypot(forward.x, forward.z) * 20;
            double verticalSpeed = Math.abs(forward.y) * 20;

            if (horizontalSpeed <= 5 && verticalSpeed <= 5) {
                return pos;
            }

            boolean shouldPredict = isMeFlying && entity.isGliding() && isHighSpeed;
            float predictMultiplier = shouldPredict ? ticks + 2 + extraForward : ticks;

            Vec3d linearPredict = pos.add(forward.multiply(predictMultiplier, predictMultiplier, predictMultiplier));
            Vec3d physicsPredict = predictElytraPhysics(entity, pos, ticks);

            double weight = MathHelper.clamp(speed / 50.0, 0.3, 0.9);

            return new Vec3d(
                    MathHelper.lerp(weight, physicsPredict.x, linearPredict.x),
                    MathHelper.lerp(weight, physicsPredict.y, linearPredict.y),
                    MathHelper.lerp(weight, physicsPredict.z, linearPredict.z)
            );
        }

        if (speed > 1) {
            return pos.add(forward.multiply(ticks, ticks, ticks));
        }

        return pos;
    }

    
    public static Vec3d predict(LivingEntity entity, Vec3d pos, int ticks) {
        PositionData data = getData(entity);

        if (data != null && entity.isGliding()) {
            Vec3d forward = data.getResolvedForward();
            double horizontalSpeed = Math.hypot(forward.x, forward.z) * 20;
            double verticalSpeed = Math.abs(forward.y) * 20;

            if (horizontalSpeed <= 5 && verticalSpeed <= 5) {
                return pos;
            }

            return pos.add(forward.multiply(ticks, ticks, ticks));
        }

        return predictElytraPhysics(entity, pos, ticks);
    }

    public static Vec3d predictElytraPhysics(LivingEntity entity, Vec3d pos, int ticks) {
        Vec3d velocity = entity.getVelocity();

        if (!entity.isGliding()) {
            return pos.add(velocity.multiply(ticks, ticks, ticks));
        }

        double horizontalDelta = Math.hypot(entity.prevX - entity.getX(), entity.prevZ - entity.getZ()) * 20;
        double verticalDelta = Math.abs(entity.getY() - entity.prevY) * 20;

        if (horizontalDelta <= 5 && verticalDelta <= 5) {
            return pos;
        }

        for (int i = 0; i < ticks; i++) {
            Vec3d rotation = entity.getRotationVector();
            float pitchRad = (float) Math.toRadians(entity.getPitch());
            double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            double velocityLength = velocity.length();
            float cos = MathHelper.cos(pitchRad);
            cos = (float) (cos * cos * Math.min(1.0D, rotation.length() / 0.4D));

            velocity = velocity.add(0.0D, -0.08D * (-1.0D + (double) cos * 0.75D), 0.0D);

            if (velocity.y < 0.0D && horizontalSpeed > 0.0D) {
                double d5 = velocity.y * -0.1D * cos;
                velocity = velocity.add(rotation.x * d5 / horizontalSpeed, d5, rotation.z * d5 / horizontalSpeed);
            }

            if (pitchRad < 0.0F && horizontalSpeed > 0.0D) {
                double lift = velocityLength * (-MathHelper.sin(pitchRad)) * 0.04D;
                velocity = velocity.add(-rotation.x * lift / horizontalSpeed, lift * 3.2D, -rotation.z * lift / horizontalSpeed);
            }

            if (horizontalSpeed > 0.0D) {
                velocity = velocity.add(
                        (rotation.x / horizontalSpeed * velocityLength - velocity.x) * 0.1D,
                        0.0D,
                        (rotation.z / horizontalSpeed * velocityLength - velocity.z) * 0.1D
                );
            }

            velocity = velocity.multiply(0.99D, 0.98D, 0.99D);
            pos = pos.add(velocity);
        }

        return pos;
    }

    public static Vec3d bypasselytrahacking(LivingEntity target) {
        Vec3d interpolatedRotation = Vec3d.fromPolar(target.getLerpTargetPitch(), target.getLerpTargetYaw());
        Vec3d rotationVector = target.getRotationVector();
        Vec3d relativePos = target.getPos().add(0, target.getHeight() * 0.6f, 0).subtract(mc.player.getEyePos());
        Vec3d blendedDirection = interpolatedRotation.normalize().lerp(rotationVector, interpolatedRotation.length());
        return relativePos.add(blendedDirection.normalize().multiply(ModuleClass.elytraTarget.forward.getValue().floatValue()));
    }

    public static void cleanup() {
        long now = System.currentTimeMillis();
        positionCache.entrySet().removeIf(e -> now - e.getValue().getLastUpdate() > 10000);
    }

    public static void clear() {
        positionCache.clear();
    }
}