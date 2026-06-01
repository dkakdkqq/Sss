package fun.lumis.client.modules.impl.player;

import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;

public class AutoTool extends Module {

    public static AutoTool INSTANCE = new AutoTool();

    private final BooleanSetting packet = new BooleanSetting("Пакетный", false);
    private final BooleanSetting silent = new BooleanSetting("Видно только для других людей", false);

    private int previousSlot = -1;

    public AutoTool() {
        super("AutoTool", "При копании берет лучший предмет", ModuleCategory.PLAYER);
        addSettings(packet, silent);
    }

    @EventLink
    public void onEvent(final EventUpdate event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.player.isCreative()) {
            this.previousSlot = -1;
            return;
        }

        if (mc.interactionManager.isBreakingBlock()) {
            if (this.previousSlot == -1) {
                this.previousSlot = mc.player.getInventory().selectedSlot;
            }

            int toolSlot = findOptimalTool();
            if (toolSlot != -1) {
                switchToSlot(toolSlot);
            }
        } else if (this.previousSlot != -1) {
            switchToSlot(this.previousSlot);
            this.previousSlot = -1;
        }
    }

    private void switchToSlot(int slot) {
        if (slot < 0 || slot > 8) return;
        if (mc.player.getInventory().selectedSlot == slot) return;

        if (silent.isState()) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        } else if (packet.isState()) {
            mc.player.getInventory().selectedSlot = slot;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        } else {
            mc.player.getInventory().selectedSlot = slot;
        }
    }

    private int findOptimalTool() {
        HitResult hitResult = mc.crosshairTarget;

        if (!(hitResult instanceof BlockHitResult blockHitResult)) {
            return -1;
        }

        BlockState blockState = mc.world.getBlockState(blockHitResult.getBlockPos());
        return findBestToolSlot(blockState);
    }

    private int findBestToolSlot(BlockState blockState) {
        int bestSlot = -1;
        float bestSpeed = 1.0F;

        for (int i = 0; i < 9; i++) {
            float speed = mc.player.getInventory().getStack(i).getMiningSpeedMultiplier(blockState);

            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    @Override
    public void onDisable() {
        this.previousSlot = -1;
        super.onDisable();
    }
}
