package fun.lumis.client.modules.impl.combat;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;

import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventPacket;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

public class NoVelocity extends Module {

    public static NoVelocity INSTANCE = new NoVelocity();

    private final ModeSetting mode = new ModeSetting("Мод", "Vanilla", "Vanilla", "Grim", "Jump Reset");
    private final BooleanSetting explosions = new BooleanSetting("Взрывы", true);

    private boolean needJump;
    private int hurtTicks;

    public NoVelocity() {
        super("NoVelocity", "Отключает отдачу от урона", ModuleCategory.MOVEMENT);
        addSettings(mode, explosions);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        needJump = false;
        hurtTicks = 0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        needJump = false;
        hurtTicks = 0;
    }

    @EventLink
    public void onPacket(EventPacket event) {
        if (mc.player == null || mc.world == null) return;
        if (event.getType() != EventPacket.Type.RECEIVE) return;

        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet) {
            if (packet.getEntityId() != mc.player.getId()) return;

            if (mode.is("Vanilla")) {
                event.cancel();
            }

            if (mode.is("Grim")) {
                event.cancel();
                double velY = packet.getVelocityY() / 8000.0;

                if (mc.player.isOnGround() && velY > 0) {
                    mc.player.setVelocity(mc.player.getVelocity().x, 0, mc.player.getVelocity().z);
                } else if (velY > 0) {
                    mc.player.setVelocity(mc.player.getVelocity().x, 0, mc.player.getVelocity().z);
                }
            }

            if (mode.is("Jump Reset")) {
                double velY = packet.getVelocityY() / 8000.0;
                if (velY > 0.1) {
                    needJump = true;
                    hurtTicks = 0;
                }
            }
        }

        if (explosions.isState() && event.getPacket() instanceof ExplosionS2CPacket) {
            if (mode.is("Vanilla") || mode.is("Grim")) {
                event.cancel();
            }
        }
    }

    
    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null) return;

        if (mode.is("Jump Reset") && needJump) {
            hurtTicks++;

            if (mc.player.isOnGround()) {
                mc.player.jump();
                needJump = false;
                hurtTicks = 0;
            }

            if (hurtTicks > 5) {
                needJump = false;
                hurtTicks = 0;
            }
        }
    }
}
