package fun.lumis.client.modules.impl.misc;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.chat.ChatUtils;
import fun.lumis.client.modules.Module;

import java.util.*;

public class LeaveTracker extends Module {

    public static LeaveTracker INSTANCE = new LeaveTracker();

    private final Map<UUID, TrackedPlayer> trackedPlayers = new HashMap<>();
    private ClientWorld lastWorld;
    private boolean initialized;

    public LeaveTracker() {
        super("LeaveTracker", "Пишет координаты ливнутых игроков из прогрузки", ModuleCategory.MISC);
    }

    @Override
    public void onDisable() {
        trackedPlayers.clear();
        initialized = false;
        super.onDisable();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            trackedPlayers.clear();
            initialized = false;
        }

        if (!initialized) {
            snapshotPlayers();
            initialized = true;
            return;
        }

        Set<UUID> seenPlayers = new HashSet<>();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) continue;

            UUID uuid = player.getUuid();
            seenPlayers.add(uuid);
            trackedPlayers.put(uuid, new TrackedPlayer(player.getName().getString(), player.getBlockPos()));
        }

        Iterator<Map.Entry<UUID, TrackedPlayer>> iterator = trackedPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TrackedPlayer> entry = iterator.next();

            if (seenPlayers.contains(entry.getKey())) continue;

            TrackedPlayer tracked = entry.getValue();
            double distSq = mc.player.squaredDistanceTo(
                    tracked.pos.getX(),
                    tracked.pos.getY(),
                    tracked.pos.getZ()
            );

            if (distSq < 65536.0) { // 256 блоков
                ChatUtils.sendMessage(
                        Formatting.GRAY + tracked.name
                                + Formatting.WHITE + " ливнул на "
                                + Formatting.GRAY + tracked.pos.getX() + " "
                                + tracked.pos.getY() + " "
                                + tracked.pos.getZ()
                );
            }
            iterator.remove();
        }
    }

    private void snapshotPlayers() {
        trackedPlayers.clear();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) continue;
            trackedPlayers.put(
                    player.getUuid(),
                    new TrackedPlayer(player.getName().getString(), player.getBlockPos())
            );
        }
    }

    private record TrackedPlayer(String name, BlockPos pos) {}
}