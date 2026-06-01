package fun.lumis.client.modules.impl.combat.components;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import fun.lumis.api.QClient;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.combat.PredictUtils;
import fun.lumis.client.modules.impl.combat.components.gcd.GCDUtil;

public abstract class RotationsSystem implements QClient {

    public Vec2f rotate = Vec2f.ZERO;

    public abstract void updateRotations(LivingEntity target);

    public static Vec2f correctRotation(float yaw, float pitch) {
        if ((yaw == -90 && pitch == 90) || yaw == -180) return new Vec2f(mc.player.getYaw(), mc.player.getPitch());

        float gcd = GCDUtil.getGCD();
        yaw -= yaw % gcd;
        pitch -= pitch % gcd;

        return new Vec2f(yaw, pitch);
    }

    protected boolean shouldUseElytraPredict(LivingEntity target) {
        return mc.player != null
                && target != null
                && mc.player.isGliding()
                && target.isGliding()
                && ModuleClass.elytraTarget != null
                && ModuleClass.elytraTarget.isEnable();
    }

    protected int getElytraPredictTicks() {
        if (ModuleClass.elytraTarget == null) {
            return 0;
        }
        return Math.max(0, ModuleClass.elytraTarget.forward.getValue().intValue());
    }

    protected Vec3d getPredictedPoint(LivingEntity target, Vec3d point) {
        if (!shouldUseElytraPredict(target)) {
            return point;
        }

        return PredictUtils.bypasselytrahacking(target);
    }

    protected Box getPredictedBox(LivingEntity target) {
        Box box = target.getBoundingBox();
        if (!shouldUseElytraPredict(target)) {
            return box;
        }
        Vec3d currentCenter = box.getCenter();
        Vec3d predictedCenter = getPredictedPoint(target, currentCenter);
        return box.offset(predictedCenter.subtract(currentCenter));
    }
}
