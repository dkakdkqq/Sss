package fun.lumis.client.modules.impl.player;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventPacket;
import fun.lumis.api.utils.chat.ChatUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.ListSetting;
import fun.lumis.mixin.SlotAccessor;

public class LockSlot extends Module {
    public static LockSlot INSTANCE = new LockSlot();

    private final ListSetting slots = new ListSetting("Слоты",
            new BooleanSetting("1", false),
            new BooleanSetting("2", false),
            new BooleanSetting("3", false),
            new BooleanSetting("4", false),
            new BooleanSetting("5", false),
            new BooleanSetting("6", false),
            new BooleanSetting("7", false),
            new BooleanSetting("8", false),
            new BooleanSetting("9", false)
    );

    public LockSlot() {
        super("LockSlot", "Блокирует выброс предметов из выбранных слотов", ModuleCategory.PLAYER);
        addSettings(slots);
    }

    @EventLink
    public void onPacket(EventPacket event) {
        if (mc.player == null || event.getType() != EventPacket.Type.SEND) return;
        if (mc.currentScreen instanceof HandledScreen<?>) return;

        if (event.getPacket() instanceof PlayerActionC2SPacket packet) {
            if (packet.getAction() != PlayerActionC2SPacket.Action.DROP_ITEM
                    && packet.getAction() != PlayerActionC2SPacket.Action.DROP_ALL_ITEMS) {
                return;
            }
            if (isCurrentSlotLockedForDrop()) {
                event.cancel();
                sendLockedMessage(mc.player.getInventory().selectedSlot);
            }
            return;
        }

        if (event.getPacket() instanceof ClickSlotC2SPacket packet && packet.getActionType() == SlotActionType.THROW) {
            int hotbarSlot = getHotbarSlotFromClick(packet.getSlot());
            if (hotbarSlot >= 0 && isHotbarSlotLocked(hotbarSlot)) {
                event.cancel();
                sendLockedMessage(hotbarSlot);
            }
        }
    }

    public boolean isCurrentSlotLockedForDrop() {
        if (!isEnable() || mc.player == null || mc.player.getMainHandStack().isEmpty()) return false;
        if (mc.currentScreen instanceof HandledScreen<?>) return false;
        return isHotbarSlotLocked(mc.player.getInventory().selectedSlot);
    }

    private boolean isHotbarSlotLocked(int slot) {
        if (slot < 0 || slot >= slots.getSettings().size()) return false;
        return slots.getSettings().get(slot).isState();
    }

    private int getHotbarSlotFromClick(int slotId) {
        if (mc.player == null || slotId < 0 || slotId >= mc.player.currentScreenHandler.slots.size()) {
            return -1;
        }

        Slot slot = mc.player.currentScreenHandler.getSlot(slotId);
        SlotAccessor accessor = (SlotAccessor) slot;
        int inventoryIndex = accessor.lumis$getIndex();
        if (accessor.lumis$getInventory() == mc.player.getInventory() && inventoryIndex >= 0 && inventoryIndex <= 8) {
            return inventoryIndex;
        }
        return -1;
    }

    private void sendLockedMessage(int slot) {
        ChatUtils.sendMessage("Выброс предмета из слота " + (slot + 1) + " заблокирован");
    }
}
