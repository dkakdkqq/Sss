package fun.lumis.api.utils.combat;

import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import fun.lumis.api.QClient;

import java.util.Objects;

@UtilityClass
public class RayTraceUtil implements QClient {
    public HitResult rayTrace(double rayTraceDistance,
                              float yaw,
                              float pitch,
                              Entity entity) {

        Vec3d startVec = mc.player.getEyePos();
        Vec3d directionVec = getVectorForRotation(pitch, yaw);

        Vec3d endVec = startVec.add(
                directionVec.x * rayTraceDistance,
                directionVec.y * rayTraceDistance,
                directionVec.z * rayTraceDistance
        );

        return mc.world.raycast(new RaycastContext(
                startVec,
                endVec,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                entity)
        );
    }

    public BlockHitResult raycast(Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType) {
        return raycast(start, end, shapeType, mc.player);
    }

    public BlockHitResult raycast(Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType, Entity entity) {
        return mc.world.raycast(new RaycastContext(start, end, shapeType, RaycastContext.FluidHandling.NONE, entity));
    }

    public boolean rayTrace(Vec3d clientVec, double range, Box box) {
        Vec3d cameraVec = Objects.requireNonNull(mc.player).getEyePos();
        return box.contains(cameraVec) || box.raycast(cameraVec,cameraVec.add(clientVec.multiply(range))).isPresent();
    }

    public boolean isViewEntity(LivingEntity target, float yaw, float pitch, float distance, boolean ignoreWalls) {
        Entity entity = mc.getCameraEntity();

        if (entity == null
                || mc.world == null)
            return false;

        double reachDistanceSquared = distance * distance;

        Vec3d startVec = entity.getEyePos();
        Vector3f directionVec = calculateViewVector(yaw, pitch);
        directionVec.mul(distance, distance, distance);
        Vec3d endVec = startVec.add(directionVec.x, directionVec.y, directionVec.z);
        Box aabb = target.getBoundingBox();

        EntityHitResult result = ProjectileUtil.raycast(
                entity,
                startVec,
                endVec,
                aabb,
                (entityIn) -> !entityIn.isSpectator() && entityIn.isAlive() && entityIn == target,
                reachDistanceSquared
        );

        return result != null;
    }

    public Vector3f calculateViewVector(float yaw, float pitch) {
        float pitchRad = pitch * 0.017453292519943295F;
        float yawRad = -yaw * 0.017453292519943295F;
        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        float cosPitch = MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);

        return new Vector3f(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    public Vec3d getVectorForRotation(float pitch, float yaw) {
        float yawRadians = -yaw * ((float) Math.PI / 180) - (float) Math.PI;
        float pitchRadians = -pitch * ((float) Math.PI / 180);

        float cosYaw = MathHelper.cos(yawRadians);
        float sinYaw = MathHelper.sin(yawRadians);
        float cosPitch = -MathHelper.cos(pitchRadians);
        float sinPitch = MathHelper.sin(pitchRadians);

        return new Vec3d(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
    }

    public boolean rayTraceSingleEntity(float yaw, float pitch, double distance, Entity entity) {
        Vec3d eyeVec = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVector(pitch, yaw);
        Vec3d extendedVec = eyeVec.add(lookVec.multiply(distance));

        Box AABB = entity.getBoundingBox();

        return AABB.contains(eyeVec) || AABB.raycast(eyeVec, extendedVec).isPresent();
    }
}