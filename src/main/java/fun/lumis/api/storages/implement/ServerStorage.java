package fun.lumis.api.storages.implement;

import lombok.Getter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import fun.lumis.api.QClient;
import fun.lumis.api.events.EventInvoker;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventPacket;
import fun.lumis.api.events.implement.EventPopTotem;
import fun.lumis.api.events.implement.EventTickPre;

import java.lang.reflect.InvocationTargetException;

@Getter
public class ServerStorage implements QClient {

    private int serverSlot;
    private float serverYaw, serverPitch, fallDistance;
    private double serverX, serverY, serverZ;
    private boolean serverOnGround, serverSprinting, serverSneaking, serverHorizontalCollision;

    public void ServerManager() {
        EventInvoker.register(this);
    }

    @EventLink
    public void onTick(EventTickPre e) {
        if (mc.player == null || mc.world == null) return;

        double y = mc.player.prevY - mc.player.getY();
        if (mc.player.isOnGround()) fallDistance = 0;
        else if (y > 0) fallDistance += (float) y;
    }

    @EventLink
    public void onPacketSend(EventPacket e) {
        if (mc.player == null || mc.world == null) return;

        if (e.getPacket() instanceof PlayerMoveC2SPacket packet) {
            if (packet.changesPosition()) {
                serverX = packet.getX(mc.player.getX());
                serverY = packet.getY(mc.player.getY());
                serverZ = packet.getZ(mc.player.getZ());
            }

            if (packet.changesLook()) {
                serverYaw = packet.getYaw(mc.player.getYaw());
                serverPitch = packet.getPitch(mc.player.getPitch());
            }

            serverOnGround = packet.isOnGround();
            serverHorizontalCollision = packet.horizontalCollision();
        }

        if (e.getPacket() instanceof UpdateSelectedSlotC2SPacket packet) serverSlot = packet.getSelectedSlot();

        if (e.getPacket() instanceof ClientCommandC2SPacket packet) {
            switch (packet.getMode()) {
                case START_SPRINTING -> {
                    e.setCancelled(serverSprinting);
                    if (!e.isCancelled()) {
                        serverSprinting = true;
                    }
                }
                case STOP_SPRINTING -> {
                    e.setCancelled(!serverSprinting);
                    if (!e.isCancelled()) {
                        serverSprinting = false;
                    }
                }
                case PRESS_SHIFT_KEY -> serverSneaking = true;
                case RELEASE_SHIFT_KEY -> serverSneaking = false;
            }
        }
    }

    @EventLink
    public void onPacketReceive(EventPacket e) throws InvocationTargetException, IllegalAccessException, InstantiationException {
        if (mc.player == null || mc.world == null) return;

        if (e.getPacket() instanceof EntityStatusS2CPacket packet && packet.getStatus() == 35) {
            if (!(packet.getEntity(mc.world) instanceof PlayerEntity player)) return;
            EventInvoker.invoke(new EventPopTotem(player));
        }
    }
}
