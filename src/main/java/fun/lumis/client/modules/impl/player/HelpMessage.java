package fun.lumis.client.modules.impl.player;

import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventBinding;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BindSetting;

public class HelpMessage extends Module {

    public static HelpMessage INSTANCE = new HelpMessage();

    private final BindSetting bind = new BindSetting("Бинд", -1);

    public HelpMessage() {
        super("HelpMessage", "Отправляет координаты в глобальный чат", ModuleCategory.PLAYER);
        addSettings(bind);
    }

    @EventLink
    public void onBinding(EventBinding event) {
        if (mc.player == null || mc.getNetworkHandler() == null || mc.currentScreen != null) {
            return;
        }

        if (event.getKey() != bind.getKey()) {
            return;
        }

        int x = mc.player.getBlockX();
        int y = mc.player.getBlockY();
        int z = mc.player.getBlockZ();
        mc.getNetworkHandler().sendChatMessage("! " + x + " " + y + " " + z);
    }
}
