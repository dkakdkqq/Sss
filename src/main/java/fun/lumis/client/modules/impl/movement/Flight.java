package fun.lumis.client.modules.impl.movement;

import net.minecraft.util.math.Vec3d;

import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;

public class Flight extends Module {

    public static Flight INSTANCE = new Flight();

    private final FloatSetting speed = new FloatSetting("Скорость", 2.0f, 0.1f, 10.0f, 0.1f);

    public Flight() {
        super("Flight", "Полёт", ModuleCategory.MOVEMENT);
        addSettings(speed);
    }

    
    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null) return;

        double spd = speed.get();
        float yaw = (float) Math.toRadians(mc.player.getYaw());

        double motionX = 0;
        double motionY = 0;
        double motionZ = 0;

        double forward = 0;
        double strafe = 0;

        if (mc.options.forwardKey.isPressed()) forward++;
        if (mc.options.backKey.isPressed()) forward--;
        if (mc.options.leftKey.isPressed()) strafe++;
        if (mc.options.rightKey.isPressed()) strafe--;

        if (forward != 0 || strafe != 0) {
            double angle = Math.atan2(forward, strafe) - Math.PI / 2;
            motionX = -Math.sin(yaw + angle) * spd;
            motionZ = Math.cos(yaw + angle) * spd;
        }

        if (mc.options.jumpKey.isPressed()) {
            motionY = spd;
        } else if (mc.options.sneakKey.isPressed()) {
            motionY = -spd;
        }

        mc.player.setVelocity(new Vec3d(motionX, motionY, motionZ));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player != null) {
            mc.player.setVelocity(Vec3d.ZERO);
        }
    }
}
