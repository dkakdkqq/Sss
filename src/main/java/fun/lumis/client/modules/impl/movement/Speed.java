package fun.lumis.client.modules.impl.movement;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

import fun.lumis.Lumis;
import fun.lumis.api.QClient;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.combat.PredictUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.impl.combat.Aura;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;

public class Speed extends Module implements QClient {

    public static Speed INSTANCE = new Speed();

    private final FloatSetting speed = new FloatSetting("Скорость", 1.0f, 0.1f, 2.0f, 0.01f);
    private final FloatSetting radius = new FloatSetting("Радиус", 1.0f, 0.01f, 3.0f, 0.1f);
    private final FloatSetting predict = new FloatSetting("Предикт", 1.0f, 0.0f, 5.0f, 0.1f);
    private final BooleanSetting onlyElytra = new BooleanSetting("Только на элитре", false);

    public Speed() {
        super("Speed", "Дополнительное ускорение", ModuleCategory.MOVEMENT);
        addSettings(speed, radius, predict, onlyElytra);
    }

    @EventLink
    private void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        collisionSpeed();
    }

    private void collisionSpeed() {
        Aura aura = ModuleClass.INSTANCE.aura;
        if (aura == null || !aura.isEnable()) return;

        LivingEntity target = aura.getTarget();
        if (target == null || target == mc.player) return;

        if (onlyElytra.isState() && !mc.player.isGliding()) return;

        Box expandedBox = mc.player.getBoundingBox().expand(radius.getValue().doubleValue());

        boolean canSpeed = false;

        if (mc.player.isGliding() || target.getBoundingBox().intersects(expandedBox)) {
            if (mc.player.isGliding()) {
                Vec3d predictedPos = PredictUtils.predict(target, target.getPos(), predict.getValue().intValue());
                double distanceToPredict = mc.player.getEyePos().distanceTo(predictedPos);
                double distanceToTarget = mc.player.getEyePos().distanceTo(target.getBoundingBox().getCenter());

                if (distanceToPredict <= 2.5D || distanceToTarget <= 2.5D) {
                    canSpeed = true;
                }
            } else {
                canSpeed = true;
            }
        }

        if (canSpeed) {
            Vec3d newVelocity = calculateVelocity(target);
            mc.player.setVelocity(newVelocity);
        }
    }

    
    @NotNull
    private Vec3d calculateVelocity(LivingEntity target) {
        double deltaX;
        double deltaZ;

        Vec3d predictedPos = PredictUtils.predict(target, target.getPos(), predict.getValue().intValue());
        deltaX = predictedPos.x - mc.player.getX();
        deltaZ = predictedPos.z - mc.player.getZ();

        float targetYaw = (float)(Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0D);
        double radYaw = Math.toRadians(targetYaw);

        double force = 0.072D * speed.getValue().doubleValue();

        Vec3d currentVelocity = mc.player.getVelocity();

        return new Vec3d(
                currentVelocity.x + -Math.sin(radYaw) * force,
                currentVelocity.y,
                currentVelocity.z + Math.cos(radYaw) * force
        );
    }
}
