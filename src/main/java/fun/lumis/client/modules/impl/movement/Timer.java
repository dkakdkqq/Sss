package fun.lumis.client.modules.impl.movement;


import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;

public class Timer extends Module {

    public static Timer INSTANCE = new Timer();

    public FloatSetting speed = new FloatSetting("Скорость", 2.0f, 0.1f, 10.0f, 0.1f);

    public Timer() {
        super("Timer", "Ускоряет время в игре", ModuleCategory.MOVEMENT);
        addSettings(speed);
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        mc.player.speed = speed.getValue().floatValue();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        mc.player.speed = 1.0f;
    }
}
