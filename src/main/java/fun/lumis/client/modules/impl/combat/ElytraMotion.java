package fun.lumis.client.modules.impl.combat;

import net.minecraft.util.math.Vec3d;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventMove;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.BooleanSetting;

public class ElytraMotion extends Module {

    public static ElytraMotion INSTANCE = new ElytraMotion();

    public FloatSetting distance = new FloatSetting("Дистанция до игрока", 3, 0, 6, 0.1f);
    public BooleanSetting bypass = new BooleanSetting("Обход", false);
    public ElytraMotion() {
        super("ElytraMotion", "Зависает рядом с игроком на эликах", ModuleCategory.COMBAT);
        addSettings(distance, bypass);
    }
    @EventLink
    public void onMove(EventMove e) {
        if (!isEnable()) return;

        Aura aura = ModuleClass.aura;
        if (mc.player == null || mc.world == null || aura.getTarget() == null) return;
        if (mc.player.isGliding() && mc.player.distanceTo(aura.getTarget()) < distance.getValue().floatValue()) {
            if (bypass.isState()) {
                float yaw = mc.player.getYaw();
                double rad = Math.toRadians(yaw);

                double forward = 0.01;
                double down = -0.0001;

                double moveX = -Math.sin(rad) * forward;
                double moveZ = Math.cos(rad) * forward;

                e.setMovePos(new Vec3d(moveX, down, moveZ));
            } else {
                e.setMovePos(Vec3d.ZERO);
            }
        }
    }
    @Override
    public void onDisable() {}
}
