package fun.lumis.client.modules.impl.movement;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventPacket;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.math.TimerUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PUBLIC)
public class PlayerFakeLags extends Module {

    public static PlayerFakeLags INSTANCE = new PlayerFakeLags();

    final ModeSetting mode = new ModeSetting("Режим", "Blink", "Blink", "Pulse");
    final FloatSetting delay = new FloatSetting("Задержка (MS)", 500, 50, 2000, 50);
    final BooleanSetting onlyMovement = new BooleanSetting("Только движение", true);

    final ObjectArrayList<Packet<?>> packets = new ObjectArrayList<>();
    final TimerUtils timer = new TimerUtils();
    boolean releasing = false;

    public PlayerFakeLags() {
        super("PlayerFakeLags", "Фейковые лаги", ModuleCategory.MOVEMENT);
        addSettings(mode, delay, onlyMovement);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        packets.clear();
        timer.reset();
        releasing = false;
    }

    @EventLink
    void onEvent(final EventUpdate ignored) {
        if (mc.player == null) return;

        if (mode.is("Pulse") && timer.finished(delay.getValue().longValue())) {
            releasePackets();
            timer.reset();
        }
    }

    
    @EventLink
    void onEvent(final EventPacket event) {
        if (mc.player == null || releasing) return;

        if (event.getType() == EventPacket.Type.SEND) {
            if (onlyMovement.isState()) {
                if (event.getPacket() instanceof PlayerMoveC2SPacket) {
                    event.cancel();
                    packets.add(event.getPacket());
                }
            } else {
                event.cancel();
                packets.add(event.getPacket());
            }
        }
    }

    private void releasePackets() {
        if (packets.isEmpty()) return;

        releasing = true;
        for (Packet<?> packet : packets) {
            mc.player.networkHandler.sendPacket(packet);
        }
        packets.clear();
        releasing = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        releasePackets();
    }
}
