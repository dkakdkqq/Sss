package fun.lumis.client.modules.impl.movement;

import net.minecraft.util.math.Vec3d;

import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;

import java.util.concurrent.ThreadLocalRandom;

public class GrimGlide extends Module {

    public static GrimGlide INSTANCE = new GrimGlide();

    private long lastTickTime = 0;
    private int ticksTwo = 0;

    public GrimGlide() {
        super("GrimGlide", "Ускорение на элитре без фееров", ModuleCategory.MOVEMENT);
    }

    
    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null || !mc.player.isGliding()) return;

        ticksTwo++;
        Vec3d pos = mc.player.getPos();
        float yaw = mc.player.getYaw();
        double forward = mc.player.age % 2 == 0 ? 0.087D : 0.09D;

        double dx = -Math.sin(Math.toRadians(yaw)) * forward;
        double dz = Math.cos(Math.toRadians(yaw)) * forward;

        if (System.currentTimeMillis() - lastTickTime >= 40) {
            mc.player.setPosition(pos.getX() + dx, pos.getY(), pos.getZ() + dz);
            lastTickTime = System.currentTimeMillis();
        }

        if (ticksTwo % 40 == 0) {
            mc.player.setVelocity(
                    dx * ThreadLocalRandom.current().nextFloat(1.001F, 1.0021F),
                    mc.player.getVelocity().y + 0.00600000075995922D,
                    dz * ThreadLocalRandom.current().nextFloat(1.001F, 1.0021F)
            );
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        ticksTwo = 0;
        lastTickTime = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        ticksTwo = 0;
        super.onDisable();
    }
}