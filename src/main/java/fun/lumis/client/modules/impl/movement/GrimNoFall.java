package fun.lumis.client.modules.impl.movement;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;

public class GrimNoFall extends Module {

    public static GrimNoFall INSTANCE = new GrimNoFall();

    public GrimNoFall() {
        super("NoFall", "Убирает урон от падения", ModuleCategory.MOVEMENT);
    }

    @EventLink
    public void onUpdate(final EventUpdate ignored) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (!mc.player.isOnGround() && mc.player.fallDistance > 1f) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY() + 0.000000001, mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), true, false));
            mc.player.onLanding();
        }
    }

}