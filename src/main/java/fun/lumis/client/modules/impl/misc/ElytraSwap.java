package fun.lumis.client.modules.impl.misc;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventBinding;
import fun.lumis.api.events.implement.EventMoveInput;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.chat.ChatUtils;
import fun.lumis.api.utils.notification.NotificationManager;
import fun.lumis.api.utils.player.InventoryUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.impl.movement.Sprint;
import fun.lumis.client.modules.settings.implement.BindSetting;
import fun.lumis.client.modules.settings.implement.BooleanSetting;

import java.util.Set;

public class ElytraSwap extends Module {

    public static ElytraSwap INSTANCE = new ElytraSwap();

    private final BindSetting elytraBind = new BindSetting("Бинд элитры", -1);
    private final BindSetting fireworkBind = new BindSetting("Бинд фейерверка", -1);
    private final BooleanSetting autofly = new BooleanSetting("Авто-взлёт", true);
    private final BooleanSetting bypassgrim = new BooleanSetting("Обходить Grim", true);
    private final BooleanSetting bypassGround = new BooleanSetting("Обходить Граунд", true);

    private boolean swapElytraQueued;
    private boolean useFirework;
    private int bypassTicks;
    private boolean sprintPaused;
    private int swapCooldown;
    private int fireworkReturnSlot = -1;
    private int fireworkReturnTicks = -1;
    private boolean packetSwapActive;
    private int packetSwapStage;
    private int packetSwapSlot;

    public ElytraSwap() {
        super("ElytraSwap", "Автоматический свап элитр", ModuleCategory.MISC);
        addSettings(elytraBind, fireworkBind, autofly, bypassgrim, bypassGround);
    }

    @EventLink
    public void onInput(final EventMoveInput e) {
        if (bypassgrim.isState() && bypassTicks > 0) {
            if (mc.player == null) return;
            mc.player.setSprinting(false);
            e.setForward(0);
            e.setStrafe(0);
            e.setJump(false);
            e.setSneak(false);
        }
    }

    @EventLink
    public void onEvent(final EventUpdate ignored) {
        if (mc.player == null) return;

        if (swapCooldown > 0) swapCooldown--;
        handleFireworkReturn();
        handlePacketSwap();

        if (bypassTicks > 0) {
            mc.player.setSprinting(false);
            bypassTicks--;
            if (bypassTicks == 1) performSwap();
            if (bypassTicks == 0) restoreSprint();
            return;
        }

        if (swapElytraQueued) {
            if (swapCooldown > 0) { swapElytraQueued = false; return; }
            if (bypassgrim.isState()) { disableSprint(); bypassTicks = 3; swapCooldown = 1; }
            else { performSwap(); swapCooldown = 1; }
            swapElytraQueued = false;
        }

        if (useFirework) {
            int slotFirework = InventoryUtils.getItemSlot(Items.FIREWORK_ROCKET);
            if (mc.player.isGliding()) {
                if (slotFirework != -1) {
                    if (bypassGround.isState()) {
                        executePacketFireworkSwap(slotFirework);
                    } else {
                        InventoryUtils.swapAndUseHvH(Items.FIREWORK_ROCKET);
                    }
                } else {
                    ChatUtils.sendMessage(Formatting.RED + "" + Formatting.BOLD + "Нет Фейерверков!");
                }
            }
            useFirework = false;
        }

        if (autofly.isState() && bypassTicks == 0) {
            ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (chestStack.isOf(Items.ELYTRA) && !mc.player.isTouchingWater() && !mc.player.isInLava()
                    && mc.player.isOnGround() && !mc.options.jumpKey.isPressed()) {
                mc.player.jump();
            } else if (chestStack.isOf(Items.ELYTRA) && isElytraUsable(chestStack)
                    && !mc.player.isGliding() && !mc.player.isOnGround()) {
                mc.player.startGliding();
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        }
    }

    private void handlePacketSwap() {
        if (!packetSwapActive || mc.player == null) return;

        if (packetSwapStage == 0) {
            int currentSlot = mc.player.getInventory().selectedSlot;
            int nextSlot = (currentSlot + 1) % 9;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(nextSlot));
            packetSwapStage = 1;
        } else if (packetSwapStage == 1) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(packetSwapSlot));
            packetSwapActive = false;
            packetSwapStage = 0;
        }
    }

    private void executePacketFireworkSwap(int fireworkSlot) {
        int currentSlot = mc.player.getInventory().selectedSlot;
        packetSwapSlot = currentSlot;

        if (fireworkSlot < 9) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(fireworkSlot));
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
        } else {
            int targetSlot = fireworkSlot >= 36 ? fireworkSlot - 36 : fireworkSlot;
            mc.interactionManager.clickSlot(0, fireworkSlot, 0, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, 36 + currentSlot, 0, SlotActionType.SWAP, mc.player);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.interactionManager.clickSlot(0, 36 + currentSlot, 0, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, fireworkSlot, 0, SlotActionType.SWAP, mc.player);
            mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
        }
        packetSwapActive = true;
        packetSwapStage = 0;
    }

    private void performSwap() {
        final int slotElytra = InventoryUtils.findBestElytraSlot();
        final int chestSlot = InventoryUtils.findBestChestplateSlot();

        boolean needChestplate = mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)
                || mc.player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                || !Set.of(Items.NETHERITE_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.IRON_CHESTPLATE,
                        Items.GOLDEN_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.LEATHER_CHESTPLATE)
                .contains(mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem());

        if (needChestplate) {
            if (chestSlot == -1) { ChatUtils.sendMessage(Formatting.RED + "" + Formatting.BOLD + "Нет нагрудника!"); bypassTicks = 0; restoreSprint(); return; }
            ItemStack chestItem = mc.player.playerScreenHandler.getSlot(chestSlot).getStack();
            doSwap(chestSlot);
        } else {
            if (slotElytra == -1) { ChatUtils.sendMessage(Formatting.RED + "" + Formatting.BOLD + "Нет элитры!"); bypassTicks = 0; restoreSprint(); return; }
            ItemStack elytraItem = mc.player.playerScreenHandler.getSlot(slotElytra).getStack();
            doSwap(slotElytra);
        }
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
    }

    private void doSwap(int slot) {
        if (slot >= 0 && slot < 9) {
            mc.interactionManager.clickSlot(0, 6, slot, SlotActionType.SWAP, mc.player);
        } else {
            mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, 6, 0, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
        }
    }

    private void handleFireworkReturn() {
        if (fireworkReturnTicks < 0) return;
        if (fireworkReturnTicks > 0) { fireworkReturnTicks--; return; }
        if (fireworkReturnSlot != -1) {
            swapSlotToOffhand(fireworkReturnSlot);
            mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
        }
        fireworkReturnSlot = -1;
        fireworkReturnTicks = -1;
    }

    private int findScreenSlot(net.minecraft.item.Item item) {
        for (int slot = 9; slot < 45; slot++) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(slot).getStack();
            if (stack.isOf(item)) return slot;
        }
        return -1;
    }

    private void swapSlotToOffhand(int slot) {
        if (slot >= 36 && slot <= 44) {
            mc.interactionManager.clickSlot(0, 45, slot - 36, SlotActionType.SWAP, mc.player);
            return;
        }
        mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
        mc.interactionManager.clickSlot(0, 45, 0, SlotActionType.SWAP, mc.player);
        mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
    }

    private void disableSprint() {
        if (sprintPaused) return;
        Sprint.pushPause(1000);
        sprintPaused = true;
    }

    private void restoreSprint() {
        if (!sprintPaused) return;
        sprintPaused = false;
        Sprint.popPause();
    }

    private boolean isElytraUsable(ItemStack stack) {
        return stack.getDamage() < stack.getMaxDamage() - 1;
    }

    @EventLink
    public void onEvent(final EventBinding event) {
        if (event.getKey() == elytraBind.getKey()) swapElytraQueued = true;
        if (event.getKey() == fireworkBind.getKey()) useFirework = true;
    }

    @Override
    public void onDisable() {
        bypassTicks = 0; swapCooldown = 0; fireworkReturnSlot = -1;
        fireworkReturnTicks = -1; packetSwapActive = false; packetSwapStage = 0;
        restoreSprint(); super.onDisable();
    }
}