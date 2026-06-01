package fun.lumis.client.modules.impl.combat;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;

public class Test extends Module {

    public static final Test INSTANCE = new Test();

    private final FloatSetting healthThreshold = new FloatSetting("Здоровье", 6.0f, 1.0f, 20.0f, 0.5f);

    private boolean eating;
    private int originalSlot = -1;

    public Test() {
        super("Test", "Автоматически ест при низком здоровье", ModuleCategory.COMBAT);
        addSettings(healthThreshold);
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

        float currentHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        if (currentHealth > healthThreshold.getValue().floatValue()) {
            if (eating && !mc.player.isUsingItem()) {
                stopEating();
            }
            return;
        }

        if (!eating) {
            int foodSlot = findFoodSlot();
            if (foodSlot == -1 && !isFood(mc.player.getOffHandStack())) {
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

        float currentHealth = player.getHealth() + player.getAbsorptionAmount();
        if (currentHealth > healthThreshold.getValue().floatValue() && !player.isUsingItem()) {
            stopEating();
            return;
        }

        if (!ensureFoodInHand()) {
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

    private boolean ensureFoodInHand() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return false;

        if (isFood(player.getOffHandStack())) {
            return true;
        }

        if (isFood(player.getMainHandStack())) {
            return true;
        }

        int foodSlot = findFoodSlot();
        if (foodSlot == -1) {
            return false;
        }

        if (foodSlot < 9) {
            selectSlot(foodSlot);
            return isFood(player.getMainHandStack());
        }

        return false;
    }

    private Hand getEatingHand(ClientPlayerEntity player) {
        if (isFood(player.getOffHandStack())) {
            return Hand.OFF_HAND;
        }
        if (isFood(player.getMainHandStack())) {
            return Hand.MAIN_HAND;
        }
        return null;
    }

    private int findFoodSlot() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return -1;

        int selected = player.getInventory().selectedSlot;
        if (isFood(player.getInventory().getStack(selected))) {
            return selected;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (slot == selected) continue;
            if (isFood(player.getInventory().getStack(slot))) {
                return slot;
            }
        }

        return -1;
    }

    private boolean isFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.isOf(Items.CHORUS_FRUIT)) return false;
        return stack.getUseAction() == UseAction.EAT;
    }

    private void selectSlot(int slot) {
        if (mc.player == null || slot < 0 || slot > 8) return;
        if (mc.player.getInventory().selectedSlot == slot) return;
        mc.player.getInventory().selectedSlot = slot;
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private void stopEating() {
        if (mc.options != null) {
            mc.options.useKey.setPressed(false);
        }
        if (eating && originalSlot != -1 && mc.player != null) {
            selectSlot(originalSlot);
        }
        eating = false;
        originalSlot = -1;
    }
}
