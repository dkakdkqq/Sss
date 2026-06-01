package fun.lumis.client.modules.impl.misc;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;

import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventBinding;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.chat.ChatUtils;
import fun.lumis.api.utils.player.InventoryUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BindSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

import java.util.List;
import java.util.Locale;

public class ServerHelper extends Module {

    public static ServerHelper INSTANCE = new ServerHelper();

    private final ModeSetting mode = new ModeSetting("Режим", "Lony", "Lony", "Spooky");

    private final BindSetting featherKey = new BindSetting("Перышко", -1)
            .visible(() -> mode.is("Lony"));
    private final BindSetting magmaKey = new BindSetting("Ливалка", -1)
            .visible(() -> mode.is("Lony"));
    private final BindSetting cryingObsidianKey = new BindSetting("Трапка", -1)
            .visible(() -> mode.is("Lony"));
    private final BindSetting clayKey = new BindSetting("Ливалка с платформой", -1)
            .visible(() -> mode.is("Lony"));

    private final BindSetting disorientationKey = new BindSetting("Дезориентация", -1)
            .visible(() -> mode.is("Spooky"));
    private final BindSetting trapKey = new BindSetting("Трапка", -1)
            .visible(() -> mode.is("Spooky"));
    private final BindSetting plastKey = new BindSetting("Пласт", -1)
            .visible(() -> mode.is("Spooky"));
    private final BindSetting pilKey = new BindSetting("Явная пыль", -1)
            .visible(() -> mode.is("Spooky"));
    private final BindSetting snegKey = new BindSetting("Снег заморозки", -1)
            .visible(() -> mode.is("Spooky"));
    private final BindSetting auraKey = new BindSetting("Божья аура", -1)
            .visible(() -> mode.is("Spooky"));

    private Item pendingItem;
    private Action pendingAction;

    public ServerHelper() {
        super("ServerHelper", "Помощник для серверов", ModuleCategory.MISC);
        addSettings(
                mode,
                featherKey,
                magmaKey,
                cryingObsidianKey,
                clayKey,
                disorientationKey,
                trapKey,
                plastKey,
                pilKey,
                snegKey,
                auraKey
        );
    }

    public boolean isSpookyMode() {
        return mode.is("Spooky");
    }

    public boolean isLonyMode() {
        return mode.is("Lony");
    }

    public List<HelperBind> getLonyHelperBinds() {
        return List.of(
                new HelperBind("Перышко", Items.FEATHER, featherKey),
                new HelperBind("Ливалка", Items.MAGMA_CREAM, magmaKey),
                new HelperBind("Трапка", Items.CRYING_OBSIDIAN, cryingObsidianKey),
                new HelperBind("Ливалка с платформой", Items.CLAY, clayKey)
        );
    }

    public List<HelperBind> getSpookyHelperBinds() {
        return List.of(
                new HelperBind("Дезориентация", Items.ENDER_EYE, disorientationKey),
                new HelperBind("Трапка", Items.NETHERITE_SCRAP, trapKey),
                new HelperBind("Пласт", Items.DRIED_KELP, plastKey),
                new HelperBind("Явная пыль", Items.SUGAR, pilKey),
                new HelperBind("Снег заморозки", Items.SNOWBALL, snegKey),
                new HelperBind("Божья аура", Items.PHANTOM_MEMBRANE, auraKey)
        );
    }

    public record HelperBind(String name, Item item, BindSetting bind) {
    }

    @Override
    public void onEnable() {
        pendingItem = null;
        pendingAction = null;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        pendingItem = null;
        pendingAction = null;
        super.onDisable();
    }

    @EventLink
    public void onBinding(EventBinding event) {
        if (mc.currentScreen != null) return;

        if (mode.is("Lony")) {
            if (event.getKey() == featherKey.getKey()) {
                pendingItem = Items.FEATHER;
            } else if (event.getKey() == magmaKey.getKey()) {
                pendingItem = Items.MAGMA_CREAM;
            } else if (event.getKey() == cryingObsidianKey.getKey()) {
                pendingItem = Items.CRYING_OBSIDIAN;
            } else if (event.getKey() == clayKey.getKey()) {
                pendingItem = Items.CLAY_BALL;
            }
            return;
        }

        if (event.getKey() == disorientationKey.getKey()) {
            pendingAction = Action.DISORIENTATION;
        } else if (event.getKey() == trapKey.getKey()) {
            pendingAction = Action.TRAP;
        } else if (event.getKey() == plastKey.getKey()) {
            pendingAction = Action.PLAST;
        } else if (event.getKey() == pilKey.getKey()) {
            pendingAction = Action.DUST;
        } else if (event.getKey() == snegKey.getKey()) {
            pendingAction = Action.FREEZE_SNOW;
        } else if (event.getKey() == auraKey.getKey()) {
            pendingAction = Action.AURA;
        }
    }

    
    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) {
            pendingItem = null;
            pendingAction = null;
            return;
        }

        if (mode.is("Lony")) {
            if (pendingItem == null) return;

            InventoryUtils.swapAndUseHvH(pendingItem);
            pendingItem = null;
            return;
        }

        if (pendingAction == null || mc.interactionManager == null) {
            return;
        }

        useAction(pendingAction);
        pendingAction = null;
    }

    private void useAction(Action action) {
        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(action.item))) {
            ChatUtils.sendMessage("У предмета " + action.cooldownName + " есть кд");
            return;
        }

        if (matchesAction(mc.player.getMainHandStack(), action)) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            ChatUtils.sendMessage(action.successText);
            return;
        }

        if (matchesAction(mc.player.getOffHandStack(), action)) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            mc.player.swingHand(Hand.OFF_HAND);
            ChatUtils.sendMessage(action.successText);
            return;
        }

        int hotbarSlot = findMatchingSlot(action, 0, 8);
        if (hotbarSlot != -1) {
            int previousSlot = mc.player.getInventory().selectedSlot;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(hotbarSlot));
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            ChatUtils.sendMessage(action.successText);
            return;
        }

        int inventorySlot = findMatchingSlot(action, 9, 35);
        if (inventorySlot != -1) {
            useFromInventory(inventorySlot);
            ChatUtils.sendMessage(action.successText);
            return;
        }

        ChatUtils.sendMessage(action.failText);
    }

    private void useFromInventory(int inventorySlot) {
        int previousSlot = mc.player.getInventory().selectedSlot;
        int hotbarSlot = findTemporaryHotbarSlot();
        boolean wasSprinting = false;

        if (mc.player.isSprinting()) {
            mc.player.networkHandler.sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
            mc.player.setSprinting(false);
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            if (!ModuleClass.INSTANCE.sprint.isEnable()) {
                mc.options.sprintKey.setPressed(false);
            }
            wasSprinting = true;
        }

        mc.interactionManager.clickSlot(0, inventorySlot, hotbarSlot, SlotActionType.SWAP, mc.player);
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(hotbarSlot));
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
        mc.interactionManager.clickSlot(0, inventorySlot, hotbarSlot, SlotActionType.SWAP, mc.player);
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));

        if (wasSprinting) {
            mc.player.networkHandler.sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));
        }
    }

    private int findTemporaryHotbarSlot() {
        int fallback = 8;

        for (int slot = 0; slot < 9; slot++) {
            if (slot == mc.player.getInventory().selectedSlot) {
                continue;
            }

            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) {
                return slot;
            }

            if (stack.getUseAction() == UseAction.NONE) {
                fallback = slot;
            }
        }

        return fallback;
    }

    private int findMatchingSlot(Action action, int start, int end) {
        for (int slot = start; slot <= end; slot++) {
            if (matchesAction(mc.player.getInventory().getStack(slot), action)) {
                return slot;
            }
        }

        return -1;
    }

    private boolean matchesAction(ItemStack stack, Action action) {
        if (stack == null || stack.isEmpty() || stack.getItem() != action.item) {
            return false;
        }

        return stack.getName().getString().toLowerCase(Locale.ROOT).contains(action.query);
    }

    private enum Action {
        DISORIENTATION("дезориентация", "дезориентации", Items.ENDER_EYE, "Использовал дезориентацию!", "Дезориентация не найдена!"),
        TRAP("трапка", "трапки", Items.NETHERITE_SCRAP, "Использовал трапку!", "Трапка не найдена!"),
        PLAST("пласт", "пласта", Items.DRIED_KELP, "Использовал пласт!", "Пласт не найден!"),
        DUST("явная пыль", "пыли", Items.SUGAR, "Использовал пыль!", "Пыль не найдена!"),
        FREEZE_SNOW("заморозка", "снега", Items.SNOWBALL, "Использовал снег!", "Снег не найден!"),
        AURA("божья", "ауры", Items.PHANTOM_MEMBRANE, "Использовал ауру!", "Аура не найдена!");

        private final String query;
        private final String cooldownName;
        private final Item item;
        private final String successText;
        private final String failText;

        Action(String query, String cooldownName, Item item, String successText, String failText) {
            this.query = query;
            this.cooldownName = cooldownName;
            this.item = item;
            this.successText = successText;
            this.failText = failText;
        }
    }
}
