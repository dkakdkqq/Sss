package fun.lumis.client.modules.impl.movement;

import fun.lumis.Lumis;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.impl.combat.Aura;
import fun.lumis.client.modules.settings.implement.BooleanSetting;

public class AutoJump extends Module {

    public static AutoJump INSTANCE = new AutoJump();

    public AutoJump() {
        super("AutoJump","Прыгает автоматически при ауре", ModuleCategory.MOVEMENT);
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        Aura aura = ModuleClass.INSTANCE.aura;

        if (aura == null || !aura.isEnable()) return;

        if (aura.getTarget() != null) {
            if (mc.player.isOnGround()) {
                mc.player.jump();
            }
        }
    }
}
