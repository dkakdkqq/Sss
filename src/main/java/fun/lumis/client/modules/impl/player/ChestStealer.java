package fun.lumis.client.modules.impl.player;

import net.minecraft.inventory.Inventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.HopperScreenHandler;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class ChestStealer extends Module {

    public static ChestStealer INSTANCE = new ChestStealer();
    
    private final FloatSetting stealDelay = new FloatSetting("Задержка", 100.0f, 0.0f, 1000.0f, 1.0f);
    private final BooleanSetting randomize = new BooleanSetting("Рандомизация", false);
    
    private long lastStealTime = 0L;

    public ChestStealer() {
        super("ChestStealer", "Автоматически открывает сундуки и забирает из них предметы", ModuleCategory.PLAYER);
        addSettings(stealDelay, randomize);
    }

    @EventLink
    private void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }

        ScreenHandler openContainer = mc.player.currentScreenHandler;
        
        if (openContainer == null || openContainer == mc.player.playerScreenHandler) {
            return;
        }

        if (!(openContainer instanceof GenericContainerScreenHandler) && 
            !(openContainer instanceof HopperScreenHandler)) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long delay = (long) stealDelay.get();
        
        if (currentTime - lastStealTime < delay) {
            return;
        }

        List<Slot> slots = openContainer.slots;
        findValidItem(slots, openContainer).ifPresent(slot -> {
            if (mc.player.currentScreenHandler == openContainer) {
                mc.interactionManager.clickSlot(
                    openContainer.syncId,
                    slot.id,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
                );
                lastStealTime = currentTime;
            }
        });
    }

    private Optional<Slot> findValidItem(List<Slot> slots, ScreenHandler handler) {
        int containerSlotCount = getContainerSlotCount(handler);
        
        if (containerSlotCount <= 0 || containerSlotCount > slots.size()) {
            return Optional.empty();
        }

        List<Slot> containerSlots = slots.subList(0, containerSlotCount);
        List<Slot> validSlots = new ArrayList<>();

        for (Slot slot : containerSlots) {
            if (slot.hasStack() && !slot.getStack().isEmpty()) {
                if (!mc.player.getItemCooldownManager().isCoolingDown(slot.getStack())) {
                    validSlots.add(slot);
                }
            }
        }

        if (validSlots.isEmpty()) {
            return Optional.empty();
        }

        if (randomize.isState()) {
            int randomIndex = ThreadLocalRandom.current().nextInt(validSlots.size());
            return Optional.of(validSlots.get(randomIndex));
        } else {
            return Optional.of(validSlots.get(0));
        }
    }

    private int getContainerSlotCount(ScreenHandler handler) {
        if (handler instanceof GenericContainerScreenHandler container) {
            Inventory inventory = container.getInventory();
            return inventory.size();
        } else if (handler instanceof HopperScreenHandler) {
            return 5; // Hopper has 5 slots
        }
        return 0;
    }

    @Override
    public void onDisable() {
        lastStealTime = 0L;
        super.onDisable();
    }
}