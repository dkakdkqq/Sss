package fun.lumis.api.utils.player;

import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import fun.lumis.api.QClient;

public record SlotSearchResult(int slot, boolean found, ItemStack stack) implements QClient {
    private static final SlotSearchResult NOT_FOUND_RESULT = new SlotSearchResult(-1, false, ItemStack.EMPTY);

    public static SlotSearchResult notFound() {
        return NOT_FOUND_RESULT;
    }

    public static @NotNull SlotSearchResult inOffhand(ItemStack stack) {
        return new SlotSearchResult(999, true, stack);
    }

    public boolean isHolding() {
        if (mc.player == null) return false;
        return isOffhand() || mc.player.getInventory().selectedSlot == slot;
    }

    public boolean isOffhand() {
        return slot == 999;
    }

    public boolean isInHotBar() {
        return slot >= 0 && slot < 9;
    }

    public void switchTo() {
        if (found && isInHotBar()) {
            HotbarUtil.switchTo(slot);
        }
    }

    public void switchToSilent() {
        if (found && isInHotBar()) {
            HotbarUtil.switchToSilent(slot);
        }
    }
}
