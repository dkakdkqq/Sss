package fun.lumis.api.utils.rotate;

import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.NotNull;

import fun.lumis.api.QClient;

import static fun.lumis.client.modules.impl.combat.components.gcd.GCDUtil.getGCDValue;

@UtilityClass
public class RotationUtils implements QClient {
    public HitResult rayTrace(double dst, float yaw, float pitch) {
        Vec3d vec3d = mc.player.getCameraPosVec(1f);
        Vec3d vec3d2 = getRotationVector(pitch, yaw);
        Vec3d vec3d3 = vec3d.add(vec3d2.x * dst, vec3d2.y * dst, vec3d2.z * dst);
        return mc.world.raycast(new RaycastContext(vec3d, vec3d3, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
    }

    Vec3d getBestVector(Entity entity) {
        Vec3d eyePos = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        double step = 0.1;
        Vec3d bestVec = null;
        double closestDistance = Double.MAX_VALUE;

        for (double x = box.minX; x <= box.maxX; x += step) {
            for (double y = box.minY; y <= box.maxY; y += step) {
                for (double z = box.minZ; z <= box.maxZ; z += step) {
                    Vec3d sample = new Vec3d(x, y, z);
                    double dist = eyePos.distanceTo(sample);
                    if (dist < closestDistance) {
                        closestDistance = dist;
                        bestVec = sample;
                    }
                }
            }
        }
        return bestVec;
    }

    public Rotation fromVec3d(Vec3d vector) {
        return new Rotation((float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(vector.z, vector.x)) - 90), (float) MathHelper.wrapDegrees(Math.toDegrees(-Math.atan2(vector.y, Math.hypot(vector.x, vector.z)))));
    }

    public @NotNull Vec3d getRotationVector(float yaw, float pitch) {
        return new Vec3d(MathHelper.sin(-pitch * 0.017453292F) * MathHelper.cos(yaw * 0.017453292F), -MathHelper.sin(yaw * 0.017453292F), MathHelper.cos(-pitch * 0.017453292F) * MathHelper.cos(yaw * 0.017453292F));
    }

    public Vec2f getRotations(Entity entity) {
        return getRotations(entity.getX(), entity.getY(), entity.getZ());
    }

    public Vec2f getRotations(Vec3d vec3d) {
        return getRotations(vec3d.x, vec3d.y, vec3d.z);
    }

    
    public Vec2f getRotations(double x, double y, double z) {
        double deltaX = x - mc.player.getX();
        double deltaY = y - mc.player.getEyeY();
        double deltaZ = z - mc.player.getZ();
        double distance = MathHelper.sqrt((float) (deltaX * deltaX + deltaZ * deltaZ));

        float yaw = (float) (MathHelper.atan2(deltaZ, deltaX) * (180D / Math.PI) - 90.0F);
        float pitch = (float) (-MathHelper.atan2(deltaY, distance) * (180D / Math.PI));
        return new Vec2f(yaw, pitch);
    }

    public float[] getRotations(Direction direction) {
        return switch (direction) {
            case DOWN -> new float[]{mc.player.getYaw(), 90.0f};
            case UP -> new float[]{mc.player.getYaw(), -90.0f};
            case NORTH -> new float[]{180.0f, mc.player.getPitch()};
            case SOUTH -> new float[]{0.0f, mc.player.getPitch()};
            case WEST -> new float[]{90.0f, mc.player.getPitch()};
            case EAST -> new float[]{-90.0f, mc.player.getPitch()};
        };
    }
    
    public float[] correctRotation(float[] rotations) {
    	rotations[0] -= rotations[0] % getGCDValue();
    	rotations[1] -= rotations[1] % getGCDValue();
    	return new float[]{rotations[0], rotations[1]};
    }

    public float getFixRotate(float rot) {
        return getDeltaMouse(rot) * getGCDValue();
    }

    public float getDeltaMouse(float delta) {
        return Math.round(delta / getGCDValue());
    }
}