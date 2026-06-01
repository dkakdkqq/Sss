package fun.lumis.client.modules.impl.movement;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventCloseInv;
import fun.lumis.api.events.implement.EventPacket;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.player.MoveUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.ModeSetting;

import java.util.ArrayList;
import java.util.List;

public class InventoryWalk extends Module {

    public static InventoryWalk INSTANCE = new InventoryWalk();

    public ModeSetting mode = new ModeSetting("Обход", "Обычный", "Обычный", "Grim");
    public ModeSetting grimVersion = new ModeSetting("Версия свапа", "1.21.4", "1.21.4", "1.16.5")
            .visible(() -> mode.is("Grim"));

    public int tick = 0;
    private final List<ClickSlotC2SPacket> pendingPackets = new ArrayList<>();
    private CloseHandledScreenC2SPacket pendingClosePacket = null;
    private boolean sprintPaused = false;
    private boolean waitingToClose = false;
    private int delayedFlushTicks = -1;
    private boolean flushingPackets = false;

    public InventoryWalk() {
        super("InventoryWalk", "Ходьба с открытым инвентарём", ModuleCategory.MOVEMENT);
        addSettings(mode, grimVersion);
    }

    @EventLink
    public void onUpdate(final EventUpdate event) {
        if (mc.player == null) return;

        final KeyBinding[] pressedKeys = {
                mc.options.forwardKey,
                mc.options.backKey,
                mc.options.leftKey,
                mc.options.rightKey,
                mc.options.jumpKey,
                mc.options.sprintKey
        };

        if (mode.is("Grim") && grimVersion.is("1.21.4") && waitingToClose && !MoveUtils.isMoving()) {
            flushQueuedPackets(true);
            waitingToClose = false;
            tick = 3;
        }

        if (mode.is("Grim") && grimVersion.is("1.16.5") && delayedFlushTicks >= 0) {
            if (delayedFlushTicks == 0) {
                flushQueuedPackets(true);
                delayedFlushTicks = -1;
                tick = 1;
            } else {
                delayedFlushTicks--;
            }
        }

        if (tick == 0 && !pendingPackets.isEmpty() && mc.currentScreen == null && !waitingToClose) {
            sendPendingPackets();
        }

        if (tick != 0) {
            for (KeyBinding keyBinding : pressedKeys) {
                keyBinding.setPressed(false);
            }
            tick--;

            if (tick == 0 && sprintPaused) {
                sprintPaused = false;
                Sprint.popPause();
            }
            return;
        }

        if (mc.currentScreen instanceof ChatScreen || mc.currentScreen instanceof SignEditScreen) {
            return;
        }

        if (mode.is("Grim") && mc.currentScreen instanceof HandledScreen && !(mc.currentScreen instanceof InventoryScreen)) {
            return;
        }

        if (waitingToClose) {
            for (KeyBinding keyBinding : pressedKeys) {
                keyBinding.setPressed(false);
            }
            return;
        }

        for (KeyBinding keyBinding : pressedKeys) {
            boolean isKeyPressed = InputUtil.isKeyPressed(mc.getWindow().getHandle(), keyBinding.getDefaultKey().getCode());
            keyBinding.setPressed(isKeyPressed);
        }
    }

    @EventLink
    public void onPacket(final EventPacket event) {
        if (event.getType() != EventPacket.Type.SEND || flushingPackets) {
            return;
        }

        Object packet = event.getPacket();
        if (!(mode.is("Grim") && MoveUtils.isMoving() && mc.currentScreen instanceof InventoryScreen)) {
            return;
        }

        if (packet instanceof ClickSlotC2SPacket clickPacket) {
            pendingPackets.add(clickPacket);
            event.cancel();
            return;
        }

        if (packet instanceof CloseHandledScreenC2SPacket closePacket) {
            pendingClosePacket = closePacket;
            if (grimVersion.is("1.16.5")) {
                delayedFlushTicks = 1;
                waitingToClose = false;
            } else {
                waitingToClose = true;
            }
            pauseSprint();
            event.cancel();
        }
    }

    @EventLink
    public void onCloseInv(final EventCloseInv eventCloseInv) {
        if (mode.is("Grim") && grimVersion.is("1.16.5") && MoveUtils.isMoving() && mc.currentScreen instanceof InventoryScreen) {
            pendingClosePacket = new CloseHandledScreenC2SPacket(eventCloseInv.windowId);
            delayedFlushTicks = 1;
            pauseSprint();
            tick = 1;
            eventCloseInv.cancel();
            return;
        }

        if (mode.is("Grim") && !waitingToClose) {
            pauseSprint();
            tick = 1;
        }
    }

    private void pauseSprint() {
        if (sprintPaused) {
            return;
        }

        Sprint.pushPause(0);
        sprintPaused = true;
    }

    private void sendPendingPackets() {
        if (mc.player == null || mc.getNetworkHandler() == null) {
            pendingPackets.clear();
            return;
        }

        flushingPackets = true;
        try {
            for (ClickSlotC2SPacket packet : pendingPackets) {
                mc.getNetworkHandler().sendPacket(packet);
            }
        } finally {
            flushingPackets = false;
        }
        pendingPackets.clear();
    }

    private void flushQueuedPackets(boolean includeClose) {
        if (mc.player == null || mc.getNetworkHandler() == null) {
            pendingPackets.clear();
            pendingClosePacket = null;
            return;
        }

        sendPendingPackets();

        if (includeClose && pendingClosePacket != null) {
            flushingPackets = true;
            try {
                mc.getNetworkHandler().sendPacket(pendingClosePacket);
            } finally {
                flushingPackets = false;
            }
            pendingClosePacket = null;
        }
    }

    public static void stopTick(int ticks) {
        InventoryWalk inventoryWalk = ModuleClass.inventoryWalk;
        if (inventoryWalk != null && inventoryWalk.isEnable()) {
            inventoryWalk.tick = Math.max(inventoryWalk.tick, ticks);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        flushQueuedPackets(true);
        if (sprintPaused) {
            sprintPaused = false;
            Sprint.popPause();
        }
        waitingToClose = false;
        delayedFlushTicks = -1;
        flushingPackets = false;
        tick = 0;
    }
}
