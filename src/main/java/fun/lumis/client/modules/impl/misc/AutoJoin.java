package fun.lumis.client.modules.impl.misc;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.item.Items;

import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventPacket;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.chat.ChatUtils;
import fun.lumis.api.utils.math.TimerUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;

public final class AutoJoin extends Module {
    public static AutoJoin INSTANCE = new AutoJoin();
    private static final long CLICK_DELAY_MS = 30L;
    private static final int NEXT_PAGE_SLOT = 44;
    private static final int MAX_PAGE_SWITCHES = 5;

    private final FloatSetting grief = new FloatSetting("Гриф", 5.0f, 1.0f, 64.0f, 1.0f);

    private final TimerUtils clickTimer = new TimerUtils();
    private final TimerUtils compassTimer = new TimerUtils();

    private boolean joining;
    private int pageSwitches;
    private int targetGrief;

    public AutoJoin() {
        super("AutoJoin", "Автоматически заходит на выбранный гриф", ModuleCategory.MISC);
        addSettings(grief);
    }

    public void startJoinTo(int griefId) {
        grief.setValue(griefId);
        if (!isEnable()) {
            toggle();
            return;
        }
        startJoin();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        startJoin();
    }

    @Override
    public void onDisable() {
        joining = false;
        pageSwitches = 0;
        super.onDisable();
    }

    
    @EventLink
    public void onUpdate(EventUpdate event) {
        if (!joining || mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (!(mc.currentScreen instanceof GenericContainerScreen)) {
            openServerSelector(false);
            return;
        }

        handleServerMenu();
    }

    @EventLink
    public void onPacket(EventPacket event) {
        if (!joining || mc.player == null || mc.world == null || event.getType() != EventPacket.Type.RECEIVE) return;

        if (event.getPacket() instanceof GameJoinS2CPacket) {
            ChatUtils.sendMessage("Вход на гриф #" + targetGrief + ": успешно");
            joining = false;
            pageSwitches = 0;
            return;
        }

        if (!(event.getPacket() instanceof GameMessageS2CPacket packet)) return;

        String message = packet.content().getString();
        if (message.contains("Подождите несколько секунд перед повторным подключением")) {
            event.cancel();
            return;
        }

        if (message.contains("К сожалению сервер переполнен")) {
            event.cancel();
            ChatUtils.sendMessage("Вход на гриф #" + targetGrief + ": неудачно");
            return;
        }

        openServerSelector(false);
    }

    private void startJoin() {
        joining = true;
        pageSwitches = 0;
        targetGrief = Math.round(grief.get());
        clickTimer.reset();
        compassTimer.reset();

        if (mc.player != null && mc.world != null) {
            openServerSelector(true);
        }
    }

    private void openServerSelector(boolean force) {
        if (!force && !compassTimer.finished(CLICK_DELAY_MS)) return;

        if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;

        int previousSlot = mc.player.getInventory().selectedSlot;
        int slot = findCompassSlot();
        if (slot == -1) {
            return;
        }

        pageSwitches = 0;
        mc.player.getInventory().selectedSlot = slot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.getInventory().selectedSlot = previousSlot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
        compassTimer.reset();
    }

    private int findCompassSlot() {
        if (mc.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.COMPASS) {
                return i;
            }
        }

        return -1;
    }

    private void handleServerMenu() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
        if (!clickTimer.finished(CLICK_DELAY_MS)) return;

        String title = screen.getTitle().getString();
        ScreenHandler handler = screen.getScreenHandler();

        if (title.contains("Выбор сервера")) {
            clickSlot(handler, 21);
            pageSwitches = 0;
            clickTimer.reset();
            return;
        }

        if (clickTargetGriefIfVisible(handler)) {
            return;
        }

        if (targetGrief > 36 && pageSwitches < MAX_PAGE_SWITCHES) {
            Slot nextPageSlot = getSlot(handler, NEXT_PAGE_SLOT);
            if (nextPageSlot != null && nextPageSlot.hasStack()) {
                clickSlot(handler, NEXT_PAGE_SLOT);
                pageSwitches++;
                clickTimer.reset();
            }
        }
    }

    private boolean clickTargetGriefIfVisible(ScreenHandler handler) {
        String targetName = "ГРИФ #" + targetGrief + " (1.16.5+)";
        String targetPrefix = "ГРИФ #" + targetGrief;

        for (int slot = 0; slot < handler.slots.size(); slot++) {
            Slot containerSlot = handler.getSlot(slot);
            if (containerSlot == null || !containerSlot.hasStack()) continue;

            String itemName = containerSlot.getStack().getName().getString();
            if (itemName.equalsIgnoreCase(targetName) || itemName.toUpperCase().contains(targetPrefix)) {
                clickSlot(handler, slot);
                pageSwitches = 0;
                clickTimer.reset();
                return true;
            }
        }

        return false;
    }

    private void clickSlot(ScreenHandler handler, int slot) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (slot < 0 || slot >= handler.slots.size()) return;

        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    private Slot getSlot(ScreenHandler handler, int slot) {
        if (slot < 0 || slot >= handler.slots.size()) return null;
        return handler.getSlot(slot);
    }
}
