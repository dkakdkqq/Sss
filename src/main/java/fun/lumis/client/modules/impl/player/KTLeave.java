package fun.lumis.client.modules.impl.player;


import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventBinding;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BindSetting;

public class KTLeave extends Module {

    public static KTLeave INSTANCE = new KTLeave();

    private boolean hasGM;
    private double lastX, lastY, lastZ;

    private BindSetting bind = new BindSetting("Кнопка лива", -1);

    public KTLeave() {
        super("KTLeave", "Позволяет ливнуть с пвп прямо в кт",ModuleCategory.PLAYER);
        addSettings(bind);
    }

    
    @EventLink
    public void onKey(final EventBinding e) {
        if (mc.player == null) return;
        if (e.getKey() == bind.getKey()) {
            hasGM = !hasGM;

            if (hasGM) {
                lastX = mc.player.getX();
                lastY = mc.player.getY();
                lastZ = mc.player.getZ();
                mc.player.setPosition(mc.player.getX() + 10, mc.player.getY() + 10, mc.player.getZ() + 10);
            } else {
                mc.player.setPosition(lastX, lastY, lastZ);
            }
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        hasGM = false;
    }
}
