package fun.lumis.client.modules.impl.player;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.impl.movement.Sprint;
import fun.lumis.client.modules.settings.implement.FloatSetting;

public class AutoEat extends Module {

    public static final AutoEat INSTANCE = new AutoEat();

    private static final String BARITONE_API_CLASS = "baritone.api.BaritoneAPI";

    private final FloatSetting hungerBars = new FloatSetting("Плашки голода", 6.0f, 1.0f, 10.0f, 1.0f);

    private boolean eating;
    private boolean sprintPaused;
    private boolean swappedFromInventory;
    private int originalSlot = -1;
    private int swappedInventorySlot = -1;

    public AutoEat() {
        super("AutoEat", "Автоматически ест при низком голоде", ModuleCategory.PLAYER);
        addSettings(hungerBars);
    }

    public static boolean shouldSuppressCombat() {
        return INSTANCE != null && INSTANCE.isEnable() && INSTANCE.eating;
    }

    @Override
    public void onDisable() {
        stopEating();
        super.onDisable();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            stopEating();
            return;
        }

        if (mc.currentScreen != null) {
            stopEating();
            return;
        }

        if (mc.player.getAbilities().creativeMode || mc.player.isSpectator()) {
            stopEating();
            return;
        }

        if (!eating) {
            if (!shouldStartEating()) {
                return;
            }
            eating = true;
            originalSlot = mc.player.getInventory().selectedSlot;
        }

        tickEating();
    }

    private void tickEating() {
        ClientPlayerEntity player = mc.player;
        if (player == null) {
            stopEating();
            return;
        }

        pauseBaritone();

        if (!sprintPaused) {
            Sprint.pushPause(0L);
            sprintPaused = true;
        }

        mc.options.attackKey.setPressed(false);

        if (!needsFood()) {
            if (!player.isUsingItem()) {
                stopEating();
            }
            return;
        }

        if (!ensureFoodReady()) {
            stopEating();
            return;
        }

        Hand eatingHand = getEatingHand(player);
        if (eatingHand == null) {
            stopEating();
            return;
        }

        mc.options.useKey.setPressed(true);

        if (!player.isUsingItem() || player.getActiveHand() != eatingHand) {
            mc.interactionManager.interactItem(player, eatingHand);
        }
    }

    private boolean shouldStartEating() {
        return needsFood() && !mc.player.isUsingItem() && (isValidFood(mc.player.getOffHandStack()) || findFoodSlot() != -1);
    }

    private boolean needsFood() {
        return mc.player != null
                && mc.player.getHungerManager().getFoodLevel() < 20
                && mc.player.getHungerManager().getFoodLevel() <= getFoodThreshold();
    }

    private int getFoodThreshold() {
        return Math.round(hungerBars.get()) * 2;
    }

    private boolean ensureFoodReady() {
        ClientPlayerEntity player = mc.player;
        if (player == null) {
            return false;
        }

        if (isValidFood(player.getOffHandStack())) {
            return true;
        }

        if (isValidFood(player.getMainHandStack())) {
            return true;
        }

        int foodSlot = findFoodSlot();
        if (foodSlot == -1) {
            return false;
        }

        if (foodSlot < 9) {
            swappedFromInventory = false;
            swappedInventorySlot = -1;
            selectHotbarSlot(foodSlot);
            return isValidFood(player.getMainHandStack());
        }

        selectHotbarSlot(originalSlot == -1 ? player.getInventory().selectedSlot : originalSlot);
        swapInventorySlotWithHotbar(foodSlot, player.getInventory().selectedSlot);
        swappedFromInventory = true;
        swappedInventorySlot = foodSlot;
        return isValidFood(player.getMainHandStack());
    }

    private Hand getEatingHand(ClientPlayerEntity player) {
        if (player == null) {
            return null;
        }
        if (isValidFood(player.getOffHandStack())) {
            return Hand.OFF_HAND;
        }
        if (isValidFood(player.getMainHandStack())) {
            return Hand.MAIN_HAND;
        }
        return null;
    }

    private int findFoodSlot() {
        ClientPlayerEntity player = mc.player;
        if (player == null) {
            return -1;
        }

        int selected = player.getInventory().selectedSlot;
        if (isValidFood(player.getInventory().getStack(selected))) {
            return selected;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (slot == selected) {
                continue;
            }
            if (isValidFood(player.getInventory().getStack(slot))) {
                return slot;
            }
        }

        for (int slot = 9; slot < 36; slot++) {
            if (isValidFood(player.getInventory().getStack(slot))) {
                return slot;
            }
        }

        return -1;
    }

    private boolean isValidFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE) || stack.isOf(Items.CHORUS_FRUIT)) {
            return false;
        }

        return stack.getUseAction() == UseAction.EAT;
    }

    private void selectHotbarSlot(int slot) {
        if (mc.player == null || slot < 0 || slot > 8 || mc.player.getInventory().selectedSlot == slot) {
            return;
        }

        mc.player.getInventory().selectedSlot = slot;
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private void swapInventorySlotWithHotbar(int inventorySlot, int hotbarSlot) {
        if (mc.player == null || mc.interactionManager == null || inventorySlot < 9 || inventorySlot > 35 || hotbarSlot < 0 || hotbarSlot > 8) {
            return;
        }

        mc.interactionManager.clickSlot(0, inventorySlot, hotbarSlot, SlotActionType.SWAP, mc.player);
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
        }
    }

    private void stopEating() {
        if (mc.options != null) {
            mc.options.useKey.setPressed(false);
        }

        if (sprintPaused) {
            Sprint.popPause();
            sprintPaused = false;
        }

        restoreHeldItem();
        eating = false;
    }

    private void restoreHeldItem() {
        if (mc.player == null || mc.interactionManager == null) {
            resetSwapState();
            return;
        }

        if (swappedFromInventory && swappedInventorySlot != -1) {
            int hotbarSlot = originalSlot == -1 ? mc.player.getInventory().selectedSlot : originalSlot;
            selectHotbarSlot(hotbarSlot);
            swapInventorySlotWithHotbar(swappedInventorySlot, hotbarSlot);
        }

        if (originalSlot != -1) {
            selectHotbarSlot(originalSlot);
        }

        resetSwapState();
    }

    private void resetSwapState() {
        swappedFromInventory = false;
        swappedInventorySlot = -1;
        originalSlot = -1;
    }

    private void pauseBaritone() {
        try {
            Object baritone = getPrimaryBaritone();
            if (baritone == null) {
                cancelVanillaBreaking();
                return;
            }

            Object pathing = invoke(baritone, "getPathingBehavior");
            if (pathing == null || !Boolean.TRUE.equals(invoke(pathing, "hasPath"))) {
                cancelVanillaBreaking();
                return;
            }

            Object input = invoke(baritone, "getInputOverrideHandler");
            if (input != null) {
                input.getClass().getMethod("clearAllKeys").invoke(input);
                Object blockBreakHelper = input.getClass().getMethod("getBlockBreakHelper").invoke(input);
                if (blockBreakHelper != null) {
                    blockBreakHelper.getClass().getMethod("stopBreakingBlock").invoke(blockBreakHelper);
                }
            }
            pathing.getClass().getMethod("requestPause").invoke(pathing);
            cancelVanillaBreaking();
        } catch (Throwable ignored) {
            cancelVanillaBreaking();
        }
    }

    private void cancelVanillaBreaking() {
        try {
            if (mc.interactionManager != null) {
                mc.interactionManager.cancelBlockBreaking();
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object getPrimaryBaritone() throws ReflectiveOperationException {
        Class<?> apiClass = Class.forName(BARITONE_API_CLASS);
        Object provider = apiClass.getMethod("getProvider").invoke(null);
        return provider == null ? null : provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName).invoke(target);
    }
}